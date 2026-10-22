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

class VolumeAtlasRealizationSuite extends munit.FunSuite:
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

  test("volume realization retains one direct partial surjection"):
    val labels = labelVolume()
    val realization = volumeRealization(volumeRef, regions, labels)

    assert(
      realization.parcellation.assignment.asInstanceOf[AnyRef] eq
        realization.parcelAssignment.asInstanceOf[AnyRef]
    )
    assert(
      realization.parcellation.parcelMetadata.asInstanceOf[AnyRef] eq
        realization.metadata.asInstanceOf[AnyRef]
    )
    assert(realization.domain.grid.sameRuntimeOwnerAs(labels.grid))
    assertEquals(
      realization.region(RegionId(10)).get.ordinalsInDomainOrder.toVector,
      Vector(0, 2)
    )
    assertEquals(
      realization.region(RegionId(20)).get.ordinalsInDomainOrder.toVector,
      Vector(1, 3)
    )
    assertEquals(
      realization.neuropublishProjection.assignments.head.targetOrdinals,
      Vector(0, 1, 0, 1)
    )
    assert(
      realization
        .validateNeuropublishProjection(realization.neuropublishProjection)
        .isRight
    )

  test("equal-size reordered and foreign parcel identities fail exact alignment"):
    val base = volumeRealization(volumeRef, regions, labelVolume())
    val reordered =
      volumeRealization(
        volumeRef,
        RegionIndex(regions.regions.reverse),
        labelVolume()
      )
    val foreign =
      volumeRealization(
        volumeRef.copy(family = "foreign"),
        regions,
        labelVolume()
      )

    assert(!base.parcelDomain.samePersistentIdentityAs(reordered.parcelDomain))
    assert(!base.parcelDomain.samePersistentIdentityAs(foreign.parcelDomain))
    assert(base.assignmentAlignedTo(reordered.parcelDomain).isLeft)
    assert(base.assignmentAlignedTo(foreign.parcelDomain).isLeft)

  test("retargeting a reordered parcel owner requires a certified bijection"):
    val base = volumeRealization(volumeRef, regions, labelVolume())
    val reordered =
      volumeRealization(
        volumeRef,
        RegionIndex(regions.regions.reverse),
        labelVolume()
      )
    val total =
      right(
        TotalMap.fromTargetOrdinals(
          base.parcelDomain,
          reordered.parcelDomain,
          Vector(1, 0)
        )
      )
    val bijection = right(Bijection.fromTotalMap(total))
    val retargeted = right(base.assignmentRetargeted(bijection))
    val target = reordered.parcelPoint(RegionId(10)).get

    assertEquals(
      retargeted.fiber(target).ordinalsInDomainOrder.toVector,
      base.region(RegionId(10)).get.ordinalsInDomainOrder.toVector
    )

  test("a volume realization rejects an image from a different live grid owner"):
    val labels = labelVolume()
    val registered =
      right(
        GridDomain.register(
          labels.grid,
          "realization voxels",
          DomainRegistry.empty
        )
      )
    val shifted =
      labelVolume(
        SampleSpaces(
          dims = Vector(2, 2, 1),
          origin = Some(Vector(10.0, 0.0, 0.0))
        )
      )
    val result =
      AtlasRealizations.volumeIn(
        registered.registry,
        volumeRef,
        regions,
        registered.value,
        shifted,
        AtlasProvenance.fromRef(volumeRef, regions)
      )

    assert(
      result match
        case Left(VolumeAtlasRealizationError.GridDomain(_)) => true
        case _ => false
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

  private def right[A](value: Either[?, A]): A =
    value.fold(error => fail(error.toString), identity)
