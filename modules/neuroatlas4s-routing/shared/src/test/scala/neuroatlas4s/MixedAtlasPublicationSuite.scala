package neuroatlas4s

import image4s.SomeSampleSpace

import neuroatlas4s.*
import consumer.volume.VolumeTestImages

import locus4s.DomainRegistry
import neuroim4s.image.{SampleSpaces}
import neurosurf4s.surface.Hemisphere as SurfaceHemisphere
import neurosurf4s.surface.HemispherePair
import neurosurf4s.surface.LabelInfo
import neurosurf4s.surface.LabeledSurface
import neurosurf4s.surface.SurfaceGeometry
import neurosurf4s.surface.SurfaceKind
import neurosurf4s.surface.TriangleMesh
import neurosurf4s.surface.VertexId

class MixedAtlasPublicationSuite extends munit.FunSuite:
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

  test("Schaefer variants define exact ordered parcel identity"):
    val sevenVolumeRef =
      Schaefer2018(
        SchaeferParcels.P100,
        YeoNetworks.Seven
      ).atlasRef(Confidence.Exact)
    val sevenSurfaceRef =
      Schaefer2018Surface(
        SchaeferParcels.P100,
        YeoNetworks.Seven
      ).atlasRef(Confidence.Exact)
    val seventeenVolumeRef =
      Schaefer2018(
        SchaeferParcels.P100,
        YeoNetworks.Seventeen
      ).atlasRef(Confidence.Exact)
    val sevenVolume = volumeAtlas(sevenVolumeRef).realization
    val sevenSurface = surfaceAtlas(sevenSurfaceRef, triangleMesh()).realization
    val seventeenVolume = volumeAtlas(seventeenVolumeRef).realization

    assertEquals(sevenVolumeRef.parcelVariant, Some("100Parcels_7Networks"))
    assert(
      sevenVolume.parcelDomain.samePersistentIdentityAs(
        sevenSurface.parcelDomain
      )
    )
    assert(
      !sevenVolume.parcelDomain.samePersistentIdentityAs(
        seventeenVolume.parcelDomain
      )
    )

  private def volumeAtlas(
      ref: AtlasRef,
      space: SomeSampleSpace = SampleSpaces(Vector(2, 2, 1))
  ): VolumeAtlas =
    VolumeAtlas.fromLabelVolume(
      ref,
      regions,
      VolumeTestImages.labelVolume(
        space,
        Array(10, 20, 10, 20)
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

