package neuroatlas4s

import neurosurf4s.surface.{
  Hemisphere as SurfaceHemisphere,
  LabelInfo,
  LabeledSurface,
  SurfaceGeometry,
  SurfaceKind,
  TriangleMesh,
  VertexId
}

class SurfaceAtlasQuotientSuite extends munit.FunSuite:
  test("surface atlases expose the same bilateral realization operations"):
    val mesh =
      TriangleMesh.fromRows(
        Vector(
          Vector(0.0, 0.0, 0.0),
          Vector(1.0, 0.0, 0.0),
          Vector(0.0, 1.0, 0.0)
        ),
        Vector((0, 1, 2))
      )
    val leftGeometry =
      SurfaceGeometry(mesh, SurfaceHemisphere.Left, SurfaceKind.Pial)
    val rightGeometry =
      SurfaceGeometry(mesh, SurfaceHemisphere.Right, SurfaceKind.Pial)
    val left =
      LabeledSurface.fromIndexed(
        leftGeometry,
        Vector(VertexId(0), VertexId(1), VertexId(2)),
        Vector(1, 1, 1),
        Vector(LabelInfo(1, "Left"))
      )
    val right =
      LabeledSurface.fromIndexed(
        rightGeometry,
        Vector(VertexId(0), VertexId(1), VertexId(2)),
        Vector(2, 2, 2),
        Vector(LabelInfo(2, "Right"))
      )
    val atlas =
      SurfaceAtlas.fromLabeledSurfaces(
        AtlasRef.surface(
          family = "quotient",
          model = "Surface",
          templateSpace = SpaceId.FsAverage,
          coordSpace = SpaceId.FsAverage,
          confidence = Confidence.Exact
        ),
        RegionIndex(
          Vector(
            AtlasRegionMetadata(RegionId(1), "Left", hemisphere = Some(Hemisphere.Left)),
            AtlasRegionMetadata(RegionId(2), "Right", hemisphere = Some(Hemisphere.Right))
          )
        ),
        left,
        right
      )
    val realization = atlas.realization

    assertEquals(realization.parcelAssignment.from.size, 6)
    assertEquals(
      realization.region(RegionId(1)).get.ordinalsInDomainOrder.toVector,
      Vector(0, 1, 2)
    )
    assertEquals(
      realization.region(RegionId(2)).get.ordinalsInDomainOrder.toVector,
      Vector(3, 4, 5)
    )
    assertEquals(
      realization
        .metadata
        .apply(realization.parcelPoint(RegionId(2)).get)
        .hemisphere,
      Some(Hemisphere.Right)
    )
