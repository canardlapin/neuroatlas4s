package neuroatlas4s

import image4s.geometry.{D3, Frame}
import neuroim4s.spaces.{FrameCatalog, Spaces as WorldFrames, TemplateName, WorldSpace}

final class TemplateCatalog private (private val frames: Map[AnySpaceId, Frame[D3]]):
  def spaces: Vector[AnySpaceId] =
    frames.keys.toVector.sortBy(_.value)

  def contains(space: AnySpaceId): Boolean =
    frames.contains(SpaceId.normalize(space))

  def frame(space: AnySpaceId): Either[AtlasError, Frame[D3]] =
    val normalized = SpaceId.normalize(space)
    frames.get(normalized).toRight(AtlasError.UnknownSpace(normalized))

  /** The world space a catalogued space's coordinates live in: its catalog frame's world. Uncatalogued spaces are
    * [[AtlasError.UnknownSpace]], never a guessed template.
    */
  def world(space: AnySpaceId): Either[AtlasError, WorldSpace] =
    val normalized = SpaceId.normalize(space)
    frame(normalized).flatMap(f => FrameCatalog.worldOf(f).left.map(_ => AtlasError.UnknownSpace(normalized)))

  /** This catalog plus fresh template frames for any of `extra` it does not already hold. */
  def including(extra: Iterable[AnySpaceId]): TemplateCatalog =
    val missing = extra.iterator.map(SpaceId.normalize).filterNot(frames.contains).toVector.distinct
    if missing.isEmpty then this
    else new TemplateCatalog(frames ++ missing.map(space => space -> TemplateCatalog.templateFrame(space.value)))

object TemplateCatalog:
  val standard: TemplateCatalog =
    new TemplateCatalog(
      Map(
        SpaceId.MNI152 -> templateFrame("MNI152"),
        SpaceId.MNI305 -> WorldFrames.MNI305,
        SpaceId.MNI152NLin6Asym -> WorldFrames.MNI152NLin6Asym,
        SpaceId.MNI152NLin2009cAsym -> WorldFrames.MNI152NLin2009cAsym,
        SpaceId.FsAverage -> WorldFrames.fsaverage,
        SpaceId.FsAverage5 -> WorldFrames.fsaverage,
        SpaceId.FsAverage6 -> WorldFrames.fsaverage,
        SpaceId.FsLR32k -> WorldFrames.fsLR,
        SpaceId.FsLR59k -> WorldFrames.fsLR,
        SpaceId.FsLR164k -> WorldFrames.fsLR
      )
    )

  private def templateFrame(name: String): Frame[D3] =
    FrameCatalog.frame(
      WorldSpace.Template(TemplateName(name).fold(error => throw new IllegalArgumentException(error.message), identity))
    )
