package neuroatlas4s


import neuroatlas4s.*

import locus4s.DomainRegistry
import neurosurf4s.surface.Hemisphere as SurfaceHemisphere
import neurosurf4s.surface.HemispherePair
import neurosurf4s.surface.LabelInfo
import neurosurf4s.surface.LabeledSurface
import neurosurf4s.surface.SurfaceGeometry
import neurosurf4s.surface.SurfaceKind
import neurosurf4s.surface.TriangleMesh
import neurosurf4s.surface.VertexId

class SurfaceAtlasPublicationSuite extends munit.FunSuite:
  private val regions =
    RegionIndex(
      Vector(
        AtlasRegionMetadata(
          RegionId(10),
          "Left",
          hemisphere = Some(Hemisphere.Left),
          network = Some(NetworkId("NetA"))
        ),
        AtlasRegionMetadata(
          RegionId(20),
          "Right",
          hemisphere = Some(Hemisphere.Right),
          network = Some(NetworkId("NetB"))
        )
      )
    )

  private val volumeRef =
    AtlasRef.volume(
      family = "publication",
      model = "Tiny",
      templateSpace = SpaceId.MNI152,
      coordSpace = SpaceId.MNI152,
      confidence = Confidence.Exact,
      parcelVariant = Some("two-parcel-v1")
    )

  private val surfaceRef =
    AtlasRef.surface(
      family = "publication",
      model = "Tiny",
      templateSpace = SpaceId.FsAverage6,
      coordSpace = SpaceId.FsAverage6,
      confidence = Confidence.Exact,
      parcelVariant = Some("two-parcel-v1")
    )


  test("surface support identity includes ordered topology but not coordinates"):
    val first =
      surfaceAtlas(surfaceRef, quadMesh(swappedFaces = false)).realization
    val reordered =
      surfaceAtlas(surfaceRef, quadMesh(swappedFaces = true)).realization
    val moved =
      surfaceAtlas(
        surfaceRef,
        quadMesh(swappedFaces = false, coordinateScale = 2.0)
      ).realization

    // Independent Python hashlib/preimage calculation in the topology identity receipt.
    assertEquals(
      first.leftSupportDomain.identity.structuralFingerprint,
      "4c7011c0f5eb306b575eb8ec6ff154537c4084fff4324a4a9c0dcaa242239068"
    )
    assertEquals(
      first.rightSupportDomain.identity.structuralFingerprint,
      "dccf1258fbdc49208abe7fccd65c0a95704eb1f787ea1fd4b598415bd5856752"
    )
    assertEquals(
      reordered.leftSupportDomain.identity.structuralFingerprint,
      "bcc68c3de25a207c6f5c0c8728d57d5e70548297966e0b25823894a87589f7eb"
    )
    assertNotEquals(
      first.leftSupportDomain.identity.structuralFingerprint,
      reordered.leftSupportDomain.identity.structuralFingerprint
    )
    assertEquals(
      first.leftSupportDomain.identity.structuralFingerprint,
      moved.leftSupportDomain.identity.structuralFingerprint
    )
    assertEquals(
      first.validateNeuropublishProjection(reordered.neuropublishProjection),
      Left(
        AtlasPublicationError.SupportDomainMismatch(
          "exact spatial identity differs"
        )
      )
    )

  test("bilateral publication retains one quotient and explicit empty fibers"):
    val realization = surfaceAtlas(surfaceRef, triangleMesh()).realization
    val projection = realization.neuropublishProjection
    val leftEmpty = projection.parcelDomain.elementKeys(1)
    val rightEmpty = projection.parcelDomain.elementKeys(0)

    assertEquals(realization.parcelAssignment.to.size, 2)
    assertEquals(
      projection.assignments.map(_.coverage),
      Vector(
        NeuropublishTargetCoverageV1.AllowEmpty(Vector(leftEmpty)),
        NeuropublishTargetCoverageV1.AllowEmpty(Vector(rightEmpty))
      )
    )

  private def surfaceAtlas(
      ref: AtlasRef,
      mesh: TriangleMesh
  ): SurfaceAtlas =
    val vertices = Vector.tabulate(mesh.vertexCount)(VertexId.apply)
    val left =
      LabeledSurface.fromIndexed(
        SurfaceGeometry(mesh, SurfaceHemisphere.Left, SurfaceKind.Pial),
        vertices,
        Vector.fill(mesh.vertexCount)(10),
        Vector(LabelInfo(10, "Left"))
      )
    val right =
      LabeledSurface.fromIndexed(
        SurfaceGeometry(mesh, SurfaceHemisphere.Right, SurfaceKind.Pial),
        vertices,
        Vector.fill(mesh.vertexCount)(20),
        Vector(LabelInfo(20, "Right"))
      )
    SurfaceAtlas.fromLabeledSurfaces(ref, regions, left, right)

  private def triangleMesh(): TriangleMesh =
    TriangleMesh.fromRows(
      Vector(
        Vector(0.0, 0.0, 0.0),
        Vector(1.0, 0.0, 0.0),
        Vector(0.0, 1.0, 0.0)
      ),
      Vector((0, 1, 2))
    )

  private def quadMesh(
      swappedFaces: Boolean,
      coordinateScale: Double = 1.0
  ): TriangleMesh =
    val faces =
      if swappedFaces then Vector((0, 2, 3), (0, 1, 2))
      else Vector((0, 1, 2), (0, 2, 3))
    TriangleMesh.fromRows(
      Vector(
        Vector(0.0, 0.0, 0.0),
        Vector(coordinateScale, 0.0, 0.0),
        Vector(coordinateScale, coordinateScale, 0.0),
        Vector(0.0, coordinateScale, 0.0)
      ),
      faces
    )
