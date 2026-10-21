package consumer.volume

import neuroatlas4s.*
import image4s.geometry.{Affine, D3, Frame}
import neuroim4s.image.{GridSpec, SpatialDims, SpatialPullbacks}
import neuroim4s.spaces.{FrameCatalog, TemplateName, WorldSpace}
import neuroim4s.transform.{TransformProvenance, WorldTransform}
import reframe4s.lie.FramedAffine

class VolumeTemplateRoutesSuite extends munit.FunSuite:
  private def ok[A](value: Either[AtlasError, A]): A = value.fold(error => fail(error.message), identity)
  private def affine(values: Double*): Affine[D3] =
    Affine.fromRowMajor[D3](values.toVector).fold(error => fail(error.message), identity)
  private val shift = affine(1,0,0,3, 0,1,0,-2, 0,0,1,1, 0,0,0,1)
  private val scale = affine(2,0,0,0, 0,3,0,0, 0,0,4,0, 0,0,0,1)
  private val a = SpaceId.MNI305
  private val b = SpaceId.MNI152
  private val c = SpaceId.MNI152NLin6Asym
  private def step(from: AnySpaceId, to: AnySpaceId, value: Affine[D3], reversible: Boolean = true): TransformStep =
    TransformStep(from, to, TransformKind.Affine, TransformBackend.InternalAffine, Confidence.Exact,
      reversible, Vector.empty, TransformStatus.Available, affine = Some(value))
  private def point(actual: Point3D, expected: Point3D): Unit =
    assertEqualsDouble(actual.x, expected.x, 1e-12)
    assertEqualsDouble(actual.y, expected.y, 1e-12)
    assertEqualsDouble(actual.z, expected.z, 1e-12)

  test("volume catalog routes retain the independent FreeSurfer affine witness"):
    val route = ok(VolumeTransforms.plan(a, b))
    val mapped = ok(route.transform(Vector(Point3D(10,-20,35)))).head
    point(mapped, Point3D(10.6941,-18.4064,36.1385))
    point(ok(VolumeTransforms.plan(b,a)).transform(Vector(mapped)).toOption.get.head, Point3D(10,-20,35))

  test("ordered noncommuting affines, reverse points and grid pullback agree"):
    val route = ok(VolumeTransforms.plan(a,c,Vector(step(a,b,shift),step(b,c,scale))))
    val input = Point3D(1,2,3)
    val expected = Point3D(8,0,16)
    point(ok(route.transform(Vector(input))).head, expected)
    point(ok(route.pullPoints(Vector(expected))).head, input)
    def grid(space: AnySpaceId) =
      GridSpec.in(ok(TemplateCatalog.standard.frame(space)))(SpatialDims(2,2,2),Affine.identity[D3]).toOption.get
    val pull = ok(route.pullback(grid(c),grid(a)))
    point(SpatialPullbacks.transform(pull,input).toOption.get,expected)
    assert(route.pullback(grid(a),grid(c)).isLeft)

  test("forward routes take precedence over a cheaper inverse and parallel ties preserve manifest order"):
    val reverse = step(c,a,Affine.identity[D3])
    val registry = Vector(step(a,b,shift),step(b,c,scale),reverse)
    val forward = ok(VolumeTransforms.plan(a,c,registry))
    assertEquals(forward.steps.length,2)
    assert(!forward.usedInverses)
    val inverse = ok(VolumeTransforms.plan(a,c,Vector(reverse)))
    assert(inverse.usedInverses)
    assert(VolumeTransforms.plan(a,c,Vector(reverse.copy(reversible=false))).isLeft)
    val tied = ok(VolumeTransforms.plan(a,b,Vector(step(a,b,shift),step(a,b,scale))))
    point(ok(tied.transform(Vector(Point3D.Origin))).head,Point3D(3,-2,1))

  test("planned or mapless routes refuse even empty coordinate demand"):
    val planned = ok(VolumeTransforms.plan(SpaceId.MNI152NLin6Asym,SpaceId.MNI152NLin2009cAsym))
    assert(planned.transform(Vector.empty).isLeft)
    assert(planned.pullPoints(Vector.empty).isLeft)
    val mapless = ok(VolumeTransforms.plan(a,b,Vector(step(a,b,shift).copy(affine=None))))
    assert(mapless.transform(Vector.empty).isLeft)
    assert(mapless.pullPoints(Vector.empty).isLeft)

  test("foreign provider frame owners and surface routes are refused"):
    def foreign(name: String): Frame[D3] = FrameCatalog.frame(WorldSpace.Template(TemplateName(name).toOption.get))
    val map = FramedAffine.betweenFrames[Frame[D3],Frame[D3],D3](foreign("MNI152"),foreign("MNI305"))(shift.inverse)
    val asset = TransformAsset(WorldTransform.Linear(map,TransformProvenance.empty),"foreign")
    assert(VolumeTransforms.plan(a,b,Vector(step(a,b,shift).withAsset(asset))).isLeft)
    assert(VolumeTransforms.plan(SpaceId.FsAverage,SpaceId.FsAverage).isLeft)
    assert(VolumeTransforms.plan(a,b,Vector(step(a,SpaceId.FsAverage,shift))).isLeft)
    val atlas = fixtures.AtlasParityFixtures.atlas()
    assertEquals(AtlasQuery.queryEither(atlas,Vector(Point3D.Origin),fromSpace=SpaceId.FsLR32k),
      Left(AtlasError.NoTransformRoute(SpaceId.FsLR32k,atlas.ref.coordSpace)))

  test("identity over an uncatalogued space cannot authorize grid pullbacks"):
    val route = ok(VolumeTransforms.plan(SpaceId.Custom,SpaceId.Custom))
    point(ok(route.transform(Vector(Point3D(1,2,3)))).head,Point3D(1,2,3))
    assertEquals(route.transform(Vector.empty),Right(Vector.empty))
    val grid = GridSpec.in(ok(TemplateCatalog.standard.frame(a)))(SpatialDims(2,2,2),Affine.identity[D3]).toOption.get
    assert(route.pullback(grid,grid).isLeft)

  test("uncatalogued query endpoints retain NoTransformRoute even with empty demand"):
    val atlas = fixtures.AtlasParityFixtures.atlas()
    val expected = Left(AtlasError.NoTransformRoute(SpaceId.Custom,atlas.ref.coordSpace))
    assertEquals(AtlasQuery.queryEither(atlas,Vector(Point3D.Origin),fromSpace=SpaceId.Custom),expected)
    assertEquals(AtlasQuery.queryEither(atlas,Vector.empty,fromSpace=SpaceId.Custom),expected)
    assertEquals(VolumeTransforms.plan(SpaceId.Custom,b).left.toOption,
      Some(AtlasError.NoTransformRoute(SpaceId.Custom,b)))
    val declared = ok(VolumeTransforms.plan(SpaceId.Custom,b,Vector(step(SpaceId.Custom,b,shift))))
    point(ok(declared.transform(Vector(Point3D.Origin))).head,Point3D(3,-2,1))

  test("uncatalogued target endpoints retain the directed NoTransformRoute"):
    assertEquals(VolumeTransforms.plan(b,SpaceId.Custom).left.toOption,
      Some(AtlasError.NoTransformRoute(b,SpaceId.Custom)))
    assertEquals(VolumeTransforms.transformCoords(Vector.empty,b,SpaceId.Custom),
      Left(AtlasError.NoTransformRoute(b,SpaceId.Custom)))

  test("an unsupported query does not conceal a genuine provider graph frame failure"):
    def foreign(name: String): Frame[D3] = FrameCatalog.frame(WorldSpace.Template(TemplateName(name).toOption.get))
    val map = FramedAffine.betweenFrames[Frame[D3],Frame[D3],D3](foreign("MNI152"),foreign("MNI305"))(shift.inverse)
    val asset = TransformAsset(WorldTransform.Linear(map,TransformProvenance.empty),"foreign")
    VolumeTransforms.plan(SpaceId.Custom,b,Vector(step(a,b,shift).withAsset(asset))) match
      case Left(AtlasError.Provider(_: VolumeTransformGraphFailure)) => ()
      case other => fail(s"expected a provider graph failure, got $other")
