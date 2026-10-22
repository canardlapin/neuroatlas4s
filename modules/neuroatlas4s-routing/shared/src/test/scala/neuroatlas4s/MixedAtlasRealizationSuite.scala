package neuroatlas4s

import image4s.SomeSampleSpace

import neuroatlas4s.*

import image4s.locus.GridDomain
import locus4s.Bijection
import locus4s.DomainRegistry
import locus4s.TotalMap
import ravel.DType.given
import neuroim4s.image.{SampleSpaces}
import neuroim4s.image.NeuroVolume
import neuroim4s.image.SomeLabelVolume
import neuroim4s.image.SomeNeuroVolume
import neurosurf4s.surface.Hemisphere as SurfaceHemisphere
import neurosurf4s.surface.HemispherePair
import neurosurf4s.surface.LabelInfo
import neurosurf4s.surface.LabeledSurface
import neurosurf4s.surface.SurfaceGeometry
import neurosurf4s.surface.SurfaceKind
import neurosurf4s.surface.TriangleMesh
import neurosurf4s.surface.VertexId

class MixedAtlasRealizationSuite extends munit.FunSuite:
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
      family = "realization",
      model = "Tiny",
      templateSpace = SpaceId.MNI152,
      coordSpace = SpaceId.MNI152,
      confidence = Confidence.Exact,
      parcelVariant = Some("two-parcel-v1")
    )

  private val surfaceRef =
    AtlasRef.surface(
      family = "realization",
      model = "Tiny",
      templateSpace = SpaceId.FsAverage6,
      coordSpace = SpaceId.FsAverage6,
      confidence = Confidence.Exact,
      parcelVariant = Some("two-parcel-v1")
    )





  test("volume and bilateral surface share exact parcel identity"):
    val volume = volumeRealization(volumeRef, regions, labelVolume())
    val surface =
      SurfaceAtlasRealizations.surface(
        surfaceRef,
        regions,
        surfacePayload(),
        AtlasProvenance.fromRef(surfaceRef, regions)
      )

    assert(volume.parcelDomain.samePersistentIdentityAs(surface.parcelDomain))
    assert(volume.assignmentAlignedTo(surface.parcelDomain).isRight)
    assertEquals(
      surface.region(RegionId(10)).get.ordinalsInDomainOrder.toVector,
      Vector(0, 1, 2)
    )
    assertEquals(
      surface.region(RegionId(20)).get.ordinalsInDomainOrder.toVector,
      Vector(3, 4, 5)
    )
    assertEquals(
      surface.neuropublishProjection.assignments.map(_.coverage),
      Vector(
        NeuropublishTargetCoverageV1.AllowEmpty(
          Vector(surface.parcelDomainRecord.elementKeys(1))
        ),
        NeuropublishTargetCoverageV1.AllowEmpty(
          Vector(surface.parcelDomainRecord.elementKeys(0))
        )
      )
    )

  private def volumeRealization(
      ref: AtlasRef,
      index: RegionIndex,
      labels: SomeLabelVolume[Int]
  ): VolumeAtlasRealization =
    AtlasRealizations.volumeFromLabels(
      ref,
      index,
      labels,
      AtlasProvenance.fromRef(ref, index)
    )

  private def labelVolume(
      space: SomeSampleSpace = SampleSpaces(Vector(2, 2, 1))
  ): SomeLabelVolume[Int] =
    val sampleSpace =
      SampleSpaces
        .requireVolumeD3(space)
        .fold(error => fail(error.message), identity)
    NeuroVolume
      .copyCategoricalFromCanonicalArray[Int](
        sampleSpace,
        Array(10, 20, 10, 20)
      )
      .map(SomeNeuroVolume.eraseSpace)
      .fold(error => fail(error.message), identity)

  private def surfacePayload(): SurfaceAtlasPayload =
    val mesh =
      TriangleMesh.fromRows(
        Vector(
          Vector(0.0, 0.0, 0.0),
          Vector(1.0, 0.0, 0.0),
          Vector(0.0, 1.0, 0.0)
        ),
        Vector((0, 1, 2))
      )
    val left =
      LabeledSurface.fromIndexed(
        SurfaceGeometry(mesh, SurfaceHemisphere.Left, SurfaceKind.Pial),
        Vector(VertexId(0), VertexId(1), VertexId(2)),
        Vector(10, 10, 10),
        Vector(LabelInfo(10, "Left"))
      )
    val right =
      LabeledSurface.fromIndexed(
        SurfaceGeometry(mesh, SurfaceHemisphere.Right, SurfaceKind.Pial),
        Vector(VertexId(0), VertexId(1), VertexId(2)),
        Vector(20, 20, 20),
        Vector(LabelInfo(20, "Right"))
      )
    SurfaceAtlasPayload(HemispherePair(left, right))

  private def right[A](value: Either[?, A]): A =
    value.fold(error => fail(error.toString), identity)
