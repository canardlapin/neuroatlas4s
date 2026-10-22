package neuroatlas4s

import neurosurf4s.surface.SurfaceKind

enum SurfaceSamplingMethod:
  case NearestLabel, RibbonMode, RibbonMean

final case class SurfaceSamplingSpec(
  surfaceKind: SurfaceKind,
  method: SurfaceSamplingMethod,
  notes: Option[String] = None
)

object SurfaceSamplingSpec:
  val defaultParcel: SurfaceSamplingSpec =
    SurfaceSamplingSpec(
      surfaceKind = SurfaceKind.Midthickness,
      method = SurfaceSamplingMethod.NearestLabel,
      notes = Some("Planning value only; no volume/surface sampling is executed by scalafim-atlas.")
    )

enum VolumeSurfaceDirection:
  case VolumeToSurface, SurfaceToVolume

final case class VolumeSurfaceTransformPlan(
  direction: VolumeSurfaceDirection,
  from: AnySpaceId,
  to: AnySpaceId,
  route: TransformPlan,
  sampling: SurfaceSamplingSpec,
  dataKind: DataKind
):
  require(route.from == SpaceId.normalize(from), "volume/surface route source must match requested source")
  require(route.to == SpaceId.normalize(to), "volume/surface route target must match requested target")
  require(steps.exists(_.kind == bridgeKind), "volume/surface transform plan must include a bridge step")

  def steps: Vector[TransformStep] =
    route.steps

  def status: TransformStatus =
    route.status

  def confidence: Confidence =
    route.confidence

  def warnings: Vector[String] =
    route.warnings

  def isExecutable: Boolean =
    route.isExecutable

  private def bridgeKind: TransformKind =
    direction match
      case VolumeSurfaceDirection.VolumeToSurface => TransformKind.VolToSurf
      case VolumeSurfaceDirection.SurfaceToVolume => TransformKind.SurfToVol

object VolumeSurfaceTransformPlan:
  def volumeToSurface(
    fromVolumeSpace: VolumeOrUnknownSpaceId,
    toSurfaceSpace: SurfaceOrUnknownSpaceId,
    dataKind: DataKind = DataKind.Parcel,
    sampling: SurfaceSamplingSpec = SurfaceSamplingSpec.defaultParcel
  ): Either[AtlasError, VolumeSurfaceTransformPlan] =
    SpaceTransforms
      .plan(fromVolumeSpace, toSurfaceSpace, dataKind)
      .map(route =>
        VolumeSurfaceTransformPlan(
          VolumeSurfaceDirection.VolumeToSurface,
          SpaceId.normalize(fromVolumeSpace),
          SpaceId.normalize(toSurfaceSpace),
          route,
          sampling,
          dataKind
        )
      )

  def surfaceToVolume(
    fromSurfaceSpace: SurfaceOrUnknownSpaceId,
    toVolumeSpace: VolumeOrUnknownSpaceId,
    dataKind: DataKind = DataKind.Parcel,
    sampling: SurfaceSamplingSpec = SurfaceSamplingSpec.defaultParcel
  ): Either[AtlasError, VolumeSurfaceTransformPlan] =
    SpaceTransforms
      .plan(fromSurfaceSpace, toVolumeSpace, dataKind)
      .map(route =>
        VolumeSurfaceTransformPlan(
          VolumeSurfaceDirection.SurfaceToVolume,
          SpaceId.normalize(fromSurfaceSpace),
          SpaceId.normalize(toVolumeSpace),
          route,
          sampling,
          dataKind
        )
      )
