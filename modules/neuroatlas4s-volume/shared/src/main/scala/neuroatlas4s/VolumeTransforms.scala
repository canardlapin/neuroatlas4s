package neuroatlas4s

import cats.Hash
import image4s.geometry.{Affine, D3, Frame}
import neuroim4s.image.{GridSpec, SpatialPullback, SpatialPullbacks}
import neuroim4s.spaces.{FrameCatalog, TemplateName, WorldSpace}
import neuroim4s.spatial.{ProviderMapBinding, VolumeDomain, VolumeGraph, VolumeMapKind, VolumeReference, VolumeRegistration, VolumeRoute, VolumeRoutingError}
import neuroim4s.transform.{PushAvailability, WorldTransform}

final case class VolumeTransformGraphFailure(cause: VolumeRoutingError[AnySpaceId, Int]) extends AtlasProviderFailure:
  def message: String = cause match
    case VolumeRoutingError.MapFailure(error) => error.message
    case VolumeRoutingError.FrameMismatch(error) => error.message
    case other => s"volume transform manifest does not form a valid spatial graph: $other"

/** Atlas policy over the provider's anatomical volume route; it owns no coordinate kernel. */
final class VolumeTransformPlan private[neuroatlas4s] (
    val from: AnySpaceId,
    val to: AnySpaceId,
    val steps: Vector[TransformStep],
    private val route: Option[VolumeRoute[AnySpaceId, Int]],
    private val catalog: TemplateCatalog
):
  def nSteps: Int = steps.length
  def usedInverses: Boolean = route.exists(_.usedInverses)
  val status: TransformStatus =
    if steps.exists(_.status == TransformStatus.Planned) then TransformStatus.Planned else TransformStatus.Available
  val confidence: Confidence =
    steps.maxBy(step => VolumeTransforms.confidenceRank(step.confidence)).confidence
  val warnings: Vector[String] =
    Option.when(status == TransformStatus.Planned)("Plan includes unimplemented/planned transform step(s).").toVector ++
      Option.when(steps.exists(s => s.confidence == Confidence.Approximate || s.confidence == Confidence.Uncertain))("Plan includes low-confidence transform step(s).").toVector ++
      Option.when(usedInverses)("Plan runs transform step(s) through their inverse.").toVector

  private def maps: Either[AtlasError, Vector[ProviderMapBinding]] =
    route.fold[Either[AtlasError, Vector[ProviderMapBinding]]](Right(Vector.empty)): value =>
      VolumeTransforms.traverse(value.steps): step =>
        step.registration.map.toRight(AtlasError.TransformNotExecutable(from, to, "step without a coordinate map")).flatMap: binding =>
          if step.inverted then binding.inverted.left.map(error => AtlasError.TransformNotExecutable(from, to, error.message))
          else Right(binding)

  def pullbackExecutability: Either[AtlasError, Unit] =
    if status == TransformStatus.Planned then Left(AtlasError.TransformNotExecutable(from, to, "unavailable steps: planned transform step(s)"))
    else maps.map(_ => ())

  def executability: Either[AtlasError, Unit] =
    pullbackExecutability.flatMap: _ =>
      maps.flatMap: values =>
        Either.cond(values.forall(_.inversePullback.nonEmpty), (), AtlasError.TransformNotExecutable(from, to, "steps with a pullback only (no forward map)"))

  def isExecutable: Boolean = executability.isRight

  def pullPoints(points: Vector[Point3D]): Either[AtlasError, Vector[Point3D]] =
    pullbackExecutability.flatMap: _ =>
      VolumeTransforms.traverse(points): point =>
        route.fold[Either[AtlasError, Point3D]](Right(point))(_.pullback(point).left.map(VolumeTransforms.failure))

  def transform(points: Vector[Point3D]): Either[AtlasError, Vector[Point3D]] =
    for
      _ <- executability
      bindings <- maps
      result <- VolumeTransforms.traverse(points): point =>
        bindings.foldLeft[Either[AtlasError, Point3D]](Right(point)): (result, binding) =>
          result.flatMap: current =>
            binding.inverted.left.map(error => AtlasError.TransformNotExecutable(from, to, error.message)).flatMap: inverse =>
              SpatialPullbacks.transform(inverse.pullback, current).left.map(error => AtlasError.InvalidCoordinate(error.message))
    yield result

  /** Forward route coordinates become the target-to-source map for the explicitly reversed grid roles. */
  def pullback[S <: Frame[D3], T <: Frame[D3]](source: GridSpec[S], target: GridSpec[T]): Either[AtlasError, SpatialPullback[T, S]] =
    def requireWorld(role: String, space: AnySpaceId, frame: Frame[D3]): Either[AtlasError, Unit] =
      catalog.world(space).flatMap: expected =>
        FrameCatalog.worldOf(frame) match
          case Right(actual) if actual == expected => Right(())
          case Right(actual) => Left(AtlasError.GridWorldMismatch(role, space, s"expected ${expected.displayName}, got ${actual.displayName}"))
          case Left(error) => Left(AtlasError.GridWorldMismatch(role, space, error.message))
    for
      _ <- requireWorld("target", from, target.frame)
      _ <- requireWorld("source", to, source.frame)
      _ <- executability
      bindings <- maps
      forward <- bindings.foldLeft[Either[AtlasError, Affine[D3]]](Right(Affine.identity[D3])): (result, binding) =>
        for
          accumulated <- result
          inverse <- binding.inverted.left.map(error => AtlasError.TransformNotExecutable(from, to, error.message))
          affine <- inverse.affineOperator.toRight(AtlasError.TransformNotExecutable(from, to, "grid pullbacks need an affine route"))
          composed <- accumulated.andThen(affine).left.map(error => AtlasError.InvalidCoordinate(error.message))
        yield composed
    yield SpatialPullbacks.affine(source, target, forward)

object VolumeTransforms:
  val mni305ToMni152: Affine[D3] = VolumeManifest.mni305ToMni152
  val mni152ToMni305: Affine[D3] = VolumeManifest.mni152ToMni305
  val manifest: Vector[TransformStep] = VolumeManifest.steps

  private[neuroatlas4s] def confidenceRank(value: Confidence): Int = value match
    case Confidence.Exact => 0
    case Confidence.High => 1
    case Confidence.Approximate => 2
    case Confidence.Uncertain => 3

  private[neuroatlas4s] def failure(error: VolumeRoutingError[AnySpaceId, Int]): AtlasError = error match
    case VolumeRoutingError.NoPath(from, to) => AtlasError.NoTransformRoute(from, to)
    case other => AtlasError.Provider(VolumeTransformGraphFailure(other))

  private[neuroatlas4s] def traverse[A, B](values: Vector[A])(f: A => Either[AtlasError, B]): Either[AtlasError, Vector[B]] =
    values.foldLeft[Either[AtlasError, Vector[B]]](Right(Vector.empty)): (result, value) =>
      result.flatMap(out => f(value).map(out :+ _))

  private def volume(space: AnySpaceId): Either[AtlasError, Unit] =
    Either.cond(SpaceId.kind(space) != SpaceKindTag.Surface, (), AtlasError.SpaceKindMismatch(space, SpaceKindTag.Volume, SpaceKindTag.Surface))

  def plan(from: AnySpaceId, to: AnySpaceId, registry: Vector[TransformStep] = manifest,
      catalog: TemplateCatalog = TemplateCatalog.standard): Either[AtlasError, VolumeTransformPlan] =
    given Hash[AnySpaceId] = Hash.by(_.value)
    val source = SpaceId.normalize(from)
    val target = SpaceId.normalize(to)
    val normalized = registry.map(step => step.copy(from = SpaceId.normalize(step.from), to = SpaceId.normalize(step.to)))
    val spaces = normalized.flatMap(step => Vector(step.from, step.to)).distinct
    val frames = catalog.including(spaces)
    for
      _ <- volume(source)
      _ <- volume(target)
      _ <- traverse(spaces)(volume)
      result <-
        if source == target then
          val step = TransformStep(source, target, TransformKind.Identity, TransformBackend.Identity, Confidence.Exact,
            reversible = true, Vector.empty, TransformStatus.Available, affine = Some(Affine.identity[D3]))
          Right(new VolumeTransformPlan(source, target, Vector(step), None, frames))
        else
          for
            domains <- traverse(spaces): space =>
              for
                frame <- frames.frame(space)
                name <- TemplateName(space.value).left.map(_ => AtlasError.UnknownSpace(space))
              yield VolumeDomain.unsampled(space, VolumeReference.Template(name), frame)
            registrations <- traverse(normalized.zipWithIndex): (step, index) =>
              for
                a <- frames.frame(step.from)
                b <- frames.frame(step.to)
                map <- (step.affine, step.asset) match
                  case (Some(forward), _) => ProviderMapBinding.affineBetween(a, b, forward.inverse).left.map(error => AtlasError.InvalidCoordinate(error.message)).map(Some(_))
                  case (_, Some(asset)) => ProviderMapBinding.fromWorldTransform(asset.transform, asset.identity).left.map(error => AtlasError.InvalidCoordinate(error.message)).map(Some(_))
                  case _ => Right(None)
                kind <- step.kind match
                  case TransformKind.Identity | TransformKind.Affine => Right(VolumeMapKind.Affine3D)
                  case TransformKind.NonlinearWarp => Right(VolumeMapKind.Warp3D)
                  case other => Left(AtlasError.InvalidCoordinate(s"volume routing cannot execute $other"))
                registration <- VolumeRegistration.build(index, step.from, step.to, kind,
                  1.0 + 10.0 * confidenceRank(step.confidence) + (if step.status == TransformStatus.Planned then 100.0 else 0.0),
                  inverseQuality(step), map).left.map(failure)
              yield registration
            graph <- VolumeGraph.build(domains, registrations).left.map(failure)
            route <- graph.path(source, target, allowInverses = true).left.map(failure)
          yield
            val descriptions = route.steps.map: edge =>
              val step = normalized(edge.registration.id)
              if !edge.inverted then step
              else step.copy(from = step.to, to = step.from, affine = step.affine.map(_.inverse), asset = None,
                notes = Some(s"Inverse of ${step.from.value}->${step.to.value}" + step.notes.fold("")(note => s": $note")))
            new VolumeTransformPlan(source, target, descriptions, Some(route), frames)
    yield result

  private def inverseQuality(step: TransformStep): Option[Double] =
    if !step.reversible then None
    else (step.affine, step.asset.map(_.transform)) match
      case (Some(_), _) => Some(1.0)
      case (_, Some(WorldTransform.Linear(_, _))) => Some(1.0)
      case (_, Some(WorldTransform.Smooth(_, _))) => Some(1.0)
      case (_, Some(value: WorldTransform.Mapped[?, ?])) => value.availability match
        case PushAvailability.FromAsset(_, _) | PushAvailability.Composed(_) => Some(1.0)
        case PushAvailability.Estimated(_) => Some(0.9)
        case PushAvailability.Unavailable() => None
      case _ => None

  def transformCoords(points: Vector[Point3D], from: AnySpaceId, to: AnySpaceId,
      registry: Vector[TransformStep] = manifest): Either[AtlasError, Vector[Point3D]] =
    plan(from, to, registry).flatMap(_.transform(points))
