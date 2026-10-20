package neuroatlas4s

import image4s.geometry.{CoordinateConvention, LengthUnit}
import image4s.locus.GridDomainRecord

private[neuroatlas4s] object VolumePublication:
  def volumeDomain(
      coordinateSpaceId: String,
      record: GridDomainRecord
  ): NeuropublishVolumeGridDomainV1 =
    val grid = record.grid.key
    val convention =
      grid.frame.convention match
        case CoordinateConvention.Unspecified => "unspecified"
        case CoordinateConvention.RAS => "RAS"
        case CoordinateConvention.LPS => "LPS"
        case other =>
          throw new IllegalArgumentException(
            s"atlas publication encodes only standard conventions, not ${CoordinateConvention.serializedName(other)}"
          )
    val unit =
      grid.frame.unit match
        case LengthUnit.Millimeter => "millimeter"
        case LengthUnit.Meter => "meter"
        case LengthUnit.Micrometer => "micrometer"
        case other =>
          throw new IllegalArgumentException(
            s"atlas publication encodes only standard units, not ${LengthUnit.serializedName(other)}"
          )
    val affine = grid.indexToFrame.rowMajor.map(value => if value == 0.0 then 0.0 else value)
    val preimage =
      AtlasPublicationBinaryV1.volumeGrid(
        coordinateSpaceId,
        convention,
        unit,
        record.layout.id,
        grid.shape,
        affine
      )
    val identity =
      NeuropublishDomainIdentityV1(
        "org.neuropublish.domain/volume-grid",
        "1",
        record.domain.size,
        AtlasPublicationSha256.hex(preimage),
        preimage
      )
    NeuropublishVolumeGridDomainV1(
      localId = "volume-support",
      identity = identity,
      coordinateSpaceId = coordinateSpaceId,
      coordinateConvention = convention,
      spatialUnit = unit,
      ordinalLayout = record.layout.id,
      shape = grid.shape,
      affineRowMajor = affine
    )
