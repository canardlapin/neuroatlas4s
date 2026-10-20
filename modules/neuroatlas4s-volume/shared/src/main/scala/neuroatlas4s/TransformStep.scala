package neuroatlas4s

import image4s.geometry.{Affine as ProviderAffine, D3}
import neuroim4s.image.SpatialPoint
import neuroim4s.transform.WorldTransform

type Point3D = SpatialPoint

object Point3D:
  val Origin: Point3D =
    SpatialPoint.Origin

  def apply(x: Double, y: Double, z: Double): Point3D =
    SpatialPoint(x, y, z)

  def fromVector(values: Vector[Double]): Point3D =
    SpatialPoint.unsafeFromVector(values, "Point3D")

enum TransformBackend:
  case Identity, InternalAffine, TemplateFlowAnts, SphereNearest, Workbench, NeuroSurf, RibbonFill

enum DataKind:
  case Parcel, Vertex, Voxel

/** A provider transform implementing a manifest step: a `neuroim4s.transform` world transform between the catalog
  * frames of the step's spaces, and an identity that determines its values (e.g. asset SHA-256 digests plus every
  * loading policy). The identity keys non-affine maps, whose values cannot be fingerprinted structurally.
  */
final case class TransformAsset(transform: WorldTransform[?, ?], identity: String):
  require(identity.trim.nonEmpty, "transform asset identity must be non-empty")

/** One entry of a transform manifest: a declared edge between two template spaces.
  *
  * A step can carry coordinates only when it is `Available` and holds either an internal `affine` (the forward,
  * source-to-target matrix) or a provider `asset`. Every other step, e.g. a TemplateFlow warp whose H5 file has not been
  * loaded, stays in the graph as a typed non-executable edge: routes through it are planned and reported, never
  * guessed.
  */
final case class TransformStep(
  from: AnySpaceId,
  to: AnySpaceId,
  kind: TransformKind,
  backend: TransformBackend,
  confidence: Confidence,
  reversible: Boolean,
  dataFiles: Vector[String],
  status: TransformStatus,
  notes: Option[String] = None,
  affine: Option[ProviderAffine[D3]] = None,
  asset: Option[TransformAsset] = None
):
  require(dataFiles.forall(_.trim.nonEmpty), "transform data file names must be non-empty")
  require(affine.isEmpty || asset.isEmpty, "a transform step carries an internal affine or a provider asset, not both")

  /** Implement this step with a loaded provider transform; the step becomes `Available`. */
  def withAsset(value: TransformAsset): TransformStep =
    copy(status = TransformStatus.Available, affine = None, asset = Some(value))
