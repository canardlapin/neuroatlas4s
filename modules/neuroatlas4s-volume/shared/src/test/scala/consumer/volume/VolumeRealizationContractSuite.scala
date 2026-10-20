package consumer.volume

import neuroatlas4s.*
import neuroatlas4s.syntax.*
import neuroim4s.image.*
import consumer.volume.fixtures.AtlasParityFixtures as Fixture

class VolumeRealizationContractSuite extends munit.FunSuite:
  test("equal geometry does not admit a foreign live grid for reduction or masking"):
    val atlas = Fixture.atlas()
    val foreignSpace = SampleSpaces(dims = Fixture.dims, spacing = Some(Vector(1.0,1.0,1.0)), origin = Some(Vector(0.0,0.0,0.0)))
    val foreign = VolumeAtlas.fromLabelVolume(Fixture.ref,Fixture.regions,
      VolumeTestImages.labelVolume(foreignSpace,Fixture.labelData()))
    assertEquals(atlas.space.grid.shape,foreign.space.grid.shape)
    AtlasReduce.summarizeVolumeEither(atlas,Fixture.dataVolume(foreign)) match
      case Left(AtlasError.ExactGridRequired(_, _)) => ()
      case other => fail(s"foreign live owner admitted: $other")
    assert(AtlasReduce.reduceSeriesEither(atlas,Fixture.dataSeries(atlas),Some(Fixture.fullMask(foreign))).isLeft)

  test("subset preserves the exact grid, sparse ids, source background and explicit empty-parcel policy"):
    val atlas = Fixture.atlas()
    val subset = atlas.subset(_.id != RegionId(50))
    assert(subset.realization.domain.grid eq atlas.realization.domain.grid)
    assertEquals(subset.regions.ids,Vector(RegionId(10),RegionId(90)))
    assertEquals(subset.query(Point3D(4,4,4)).head.region,None)
    assertEquals(subset.realization.neuropublishProjection.displayOrder,subset.realization.displayOrder.indices.map(subset.realization.parcelKeys.apply).toVector)
    assert(AtlasReduce.reduceSeriesEither(subset,Fixture.dataSeries(atlas),Some(Fixture.emptyMask(atlas)),empty=EmptyParcelPolicy.Reject).isLeft)
    val reduced = AtlasReduce.summarizeVolume(subset,Fixture.dataVolume(atlas),values => values.sum / values.length)
    assertEqualsDouble(reduced.value(RegionId(10)).get,5.0,1e-12)
    assertEqualsDouble(reduced.value(RegionId(90)).get,13.0,1e-12)
