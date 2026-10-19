package neuroatlas4s

/** Catalog, label and error semantics that need no volume or surface runtime. */
class AtlasCatalogSuite extends munit.FunSuite:
  private final case class ToyProviderFailure(message: String) extends AtlasProviderFailure

  test("registry normalizes aliases and reports unknown ids with available atlases"):
    val spec = AtlasRegistry.default.find("Glasser 360 Surface").toOption.get
    assertEquals(spec.id, "glasser-surface")

    AtlasRegistry.default.find("not-a-real-atlas") match
      case Left(err @ AtlasError.UnknownAtlas(name, available)) =>
        assertEquals(name, "not-a-real-atlas")
        assert(available.contains("schaefer"), clue = available.mkString(","))
        assert(err.message.contains("available atlases: schaefer, glasser"), clue = err.message)
      case other =>
        fail(s"expected unknown atlas error, got $other")

    val thrown = intercept[NoSuchElementException]:
      AtlasRegistry.default("not-a-real-atlas")
    assert(thrown.getMessage.contains("unknown atlas 'not-a-real-atlas'"), clue = thrown.getMessage)

  test("region index normalizes labels, filters hemispheres, and rejects duplicate ids"):
    val index =
      RegionIndex(
        Vector(
          AtlasRegionMetadata(RegionId(1), "Left V1", labelFull = Some("Network/Left V1"), hemisphere = Some(Hemisphere.Left)),
          AtlasRegionMetadata(RegionId(2), "Left-V1", labelFull = Some("Network/Right V1"), hemisphere = Some(Hemisphere.Right)),
          AtlasRegionMetadata(RegionId(3), "Area 3", labelFull = Some("Full Area 3"), hemisphere = Some(Hemisphere.Bilateral))
        )
      )

    assertEquals(index.find("left v1").map(_.id), Vector(RegionId(1), RegionId(2)))
    assertEquals(index.find("network left v1").map(_.id), Vector(RegionId(1)))
    assertEquals(index.find("LEFT_V1", Some(Hemisphere.Left)).map(_.id), Vector(RegionId(1)))
    assertEquals(index.filter(_.hemisphere.contains(Hemisphere.Left)).ids, Vector(RegionId(1)))
    assertEquals(index.regions.head.typedLabel, RegionLabel.unsafe("Left V1"))
    assertEquals(index.regions.head.typedFullLabel, RegionLabel.unsafe("Network/Left V1"))
    assertEquals(
      AtlasRegionMetadata.checked(RegionId(10), "Area 10", attributes = Map("system" -> "visual")).map(_.typedAttributes.toMap),
      Right(Map("system" -> "visual"))
    )
    assertEquals(
      AtlasRegionMetadata.checked(RegionId(10), " ", attributes = Map.empty),
      Left(AtlasError.InvalidRegionMetadata("region label must be non-empty"))
    )

    val missing = intercept[NoSuchElementException]:
      index.requireRegion(RegionId(99))
    assertEquals(missing.getMessage, "atlas payload is missing region id 99")

    val duplicate = intercept[IllegalArgumentException]:
      RegionIndex(Vector(AtlasRegionMetadata(RegionId(1), "A"), AtlasRegionMetadata(RegionId(1), "B")))
    assert(duplicate.getMessage.contains("atlas region ids must be unique: 1"), clue = duplicate.getMessage)

  test("atlas error messages and provenance helper aliases remain explicit"):
    val errors =
      Vector(
        AtlasError.EmptyAtlas -> "atlas must contain at least one region",
        AtlasError.DuplicateRegionIds(Vector(RegionId(1), RegionId(2))) -> "atlas region ids must be unique: 1, 2",
        AtlasError.MissingRegionId(RegionId(3)) -> "atlas payload is missing region id 3",
        AtlasError.UnknownAtlas("missing", Vector("schaefer", "glasser")) -> "unknown atlas 'missing'; available atlases: schaefer, glasser",
        AtlasError.UnknownSpace(SpaceId("bad")) -> "unknown space 'bad'",
        AtlasError.SpaceKindMismatch(SpaceId.FsAverage, SpaceKindTag.Volume, SpaceKindTag.Surface) -> "space 'fsaverage' has kind Surface; expected Volume",
        AtlasError.NoTransformRoute(SpaceId.Custom, SpaceId.MNI152) -> "no transform route found from 'custom' to 'MNI152'",
        AtlasError.TransformNotExecutable(SpaceId.FsAverage, SpaceId.FsLR32k, "non-affine") -> "transform route from 'fsaverage' to 'fsLR_32k' is not executable: non-affine",
        AtlasError.SpaceMismatch(Vector(2, 2, 1), Vector(2, 1, 1)) -> "expected spatial dimensions 2x2x1 but got 2x1x1",
        AtlasError.ExactGridRequired("expected-grid", "actual-grid") -> "exact atlas grid required; expected expected-grid but got actual-grid",
        AtlasError.Provider(ToyProviderFailure("provider refused")) -> "provider refused",
        AtlasError.InvalidQuery("bad query") -> "bad query",
        AtlasError.InvalidCoordinate("bad coordinate") -> "bad coordinate",
        AtlasError.InvalidRegionMetadata("bad metadata") -> "bad metadata"
      )
    errors.foreach { case (error, expected) => assertEquals(error.message, expected) }

    assertEquals(ArtifactRole.fromLegacy("volume"), ArtifactRole.ParcellationVolume)
    assertEquals(ArtifactRole.fromLegacy("annotation"), ArtifactRole.SurfaceAnnotation)
    assertEquals(ArtifactRole.fromLegacy("lut"), ArtifactRole.LabelTable)
    assertEquals(ArtifactRole.fromLegacy("xfm"), ArtifactRole.Transform)
    assertEquals(ArtifactRole.fromLegacy("docs"), ArtifactRole.Documentation)
    assertEquals(ArtifactRole.fromLegacy("something else"), ArtifactRole.Other)

    val schema = LabelTableSchema("toy labels", Vector("id", "label"))
    assertEquals(schema.columns, Vector("id", "label"))

    interceptMessage[IllegalArgumentException]("requirement failed: label table schema must contain columns"):
      LabelTableSchema("toy labels", Vector.empty)

    val textCitation = Citation(text = Some("A textual citation."))
    assertEquals(textCitation.text, Some("A textual citation."))

    interceptMessage[IllegalArgumentException]("requirement failed: citation requires DOI or text"):
      Citation()

  test("registry resolves standard atlas aliases") {
    val spec = AtlasRegistry.default("hcp-mmp")
    assertEquals(spec.id, "glasser")
    assertEquals(spec.defaultSpace, SpaceId.MNI152NLin2009cAsym)
    assert(AtlasRegistry.default.ids.contains("schaefer"), clue = "")

    val schaefer = Schaefer2018(SchaeferParcels.P400, YeoNetworks.Seventeen, VoxelResolution.TwoMm)
    val typedVolumeRef: VolumeAtlasRef = schaefer.atlasRef()
    assertEquals(schaefer.id, "schaefer-400-17-2mm")
    assertEquals(typedVolumeRef.templateSpace, SpaceId.MNI152NLin6Asym)

    val schaeferSurface: SurfaceAtlasRef = Schaefer2018Surface.default.atlasRef()
    assertEquals(schaeferSurface.representation, AtlasRepresentation.Surface)
    assertEquals(schaeferSurface.templateSpace, SpaceId.FsAverage6)
    assertEquals(schaeferSurface.density, Some("41k"))

    val glasserSurfaceSpec = AtlasRegistry.default("hcp-mmp-surface")
    assertEquals(glasserSurfaceSpec.id, "glasser-surface")
    assertEquals(glasserSurfaceSpec.defaultSpace, SpaceId.FsLR32k)
  }
