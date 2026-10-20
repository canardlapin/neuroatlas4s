package neuroatlas4s

import image4s.geometry.GeometryError
import neuroim4s.transform.TransformError

/** Image geometry refused an atlas operation, e.g. two label grids are not congruent. */
final case class GeometryFailure(cause: GeometryError) extends AtlasProviderFailure:
  def message: String = cause.message

/** A template transform could not be read, materialised or inverted. */
final case class TransformFailure(cause: TransformError) extends AtlasProviderFailure:
  def message: String = cause.message
