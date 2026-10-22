package neuroatlas4s

import locus4s.{CertifiedMapError, DomainError, DomainRecord, DomainRegistry, DomainRestoreError, FiniteSpace, PartialMapError, SelectionError, Surjection, TotalMapError}
import locus4s.data.{FieldConstructionError, VectorField}

enum AtlasRealizationError:
  case InvalidAtlas(error: AtlasError)
  case InvalidDomain(error: DomainError)
  case DomainRestore(error: DomainRestoreError)
  case PartialMap(error: PartialMapError)
  case TotalMap(error: TotalMapError)
  case CertifiedMap(error: CertifiedMapError)
  case Field(error: FieldConstructionError)
  case Selection(error: SelectionError)
  case Publication(error: AtlasPublicationError)

  def message: String =
    this match
      case InvalidAtlas(error) => error.message
      case InvalidDomain(error) => error.message
      case DomainRestore(error) => error.message
      case PartialMap(error) => error.message
      case TotalMap(error) => error.message
      case CertifiedMap(error) => error.message
      case Field(error) => error.message
      case Selection(error) => error.message
      case Publication(error) => error.message

private[neuroatlas4s] object AtlasRealizationAdmission:
  def validateCoverage(
      assignments: Vector[Option[Int]],
      regions: RegionIndex
  ): Either[AtlasRealizationError, Unit] =
    val present = assignments.iterator.flatten.toSet
    val missing =
      regions.ids.zipWithIndex.collect:
        case (id, ordinal) if !present.contains(ordinal) => id
    if missing.isEmpty then Right(())
    else
      Left(
        AtlasRealizationError.InvalidAtlas(
          AtlasError.MissingPayloadRegionIds(missing)
        )
      )

  def validateProvenanceCoherence(
      ref: AtlasRef,
      regions: RegionIndex,
      provenance: AtlasProvenance,
      expectedRepresentation: AtlasRepresentation
  ): Either[AtlasRealizationError, Unit] =
    val error = AtlasPublicationError.AtlasProvenanceMismatch.apply
    val expectedEncoding =
      expectedRepresentation match
        case AtlasRepresentation.Volume => LabelEncoding.VolumeIntegerLabels
        case AtlasRepresentation.Surface => LabelEncoding.SurfaceIntegerLabels
        case AtlasRepresentation.Derived => LabelEncoding.DerivedLabels
    val supportMatches =
      (expectedRepresentation, provenance.support) match
        case (
              AtlasRepresentation.Volume,
              SpatialSupport.Volume(template, coordinate, _, _)
            ) =>
          template == ref.templateSpace && coordinate == ref.coordSpace
        case (
              AtlasRepresentation.Surface,
              SpatialSupport.Surface(template, _, _)
            ) =>
          template == ref.templateSpace
        case (
              AtlasRepresentation.Derived,
              SpatialSupport.Derived(template, coordinate)
            ) =>
          template == ref.templateSpace && coordinate == ref.coordSpace
        case _ => false
    val detail =
      if ref.representation != expectedRepresentation then
        Some("atlas reference representation disagrees with realization kind")
      else if provenance.identity.family != ref.family ||
          provenance.identity.model != ref.model
      then Some("atlas identity disagrees with atlas reference")
      else if provenance.labels.encoding != expectedEncoding then
        Some("label encoding disagrees with realization kind")
      else if provenance.labels.regionIds != regions.ids.sorted then
        Some("label-schema region ids differ from region metadata")
      else if provenance.labels.background != Some(0) then
        Some("hard-label realizations require source background label 0")
      else if !supportMatches then
        Some("declared spatial support disagrees with atlas reference")
      else None
    detail match
      case Some(value) =>
        Left(AtlasRealizationError.Publication(error(value)))
      case None => Right(())

  def networkAssignment[P](
      registry: DomainRegistry,
      parcels: FiniteSpace[P],
      regions: RegionIndex
  ): Either[
    AtlasRealizationError,
    (DomainRegistry, Option[AtlasNetworkAssignment[P]])
  ] =
    val assigned = regions.regions.map(_.network)
    if assigned.exists(_.isEmpty) then Right((registry, None))
    else
      val ids = assigned.flatten.distinct
      val domainId =
        s"${parcels.id.value}:networks:${ids.map(_.value).mkString(",")}"
      for
        record <- DomainRecord
          .parse(
            id = domainId,
            name = s"${parcels.name.value} networks",
            size = ids.length
          )
          .left
          .map(AtlasRealizationError.InvalidDomain.apply)
        resolution <- registry
          .restore(record)
          .left
          .map(AtlasRealizationError.DomainRestore.apply)
        surjection <- Surjection
          .fromTargetOrdinals(
            parcels,
            resolution.space,
            assigned.flatten.map(ids.zipWithIndex.toMap)
          )
          .left
          .map(totalAssignmentError)
        idField <- VectorField
          .fromValues(resolution.space, ids)
          .left
          .map(AtlasRealizationError.Field.apply)
      yield
        type Network = resolution.S
        val value =
          new AtlasNetworkAssignment[P]:
            type N = Network
            val networkIds: VectorField[Network, NetworkId] = idField
            val parcelToNetwork: Surjection[P, Network] = surjection
        (resolution.registry, Some(value))

  def partialAssignmentError(
      error: PartialMapError | CertifiedMapError
  ): AtlasRealizationError =
    error match
      case value: PartialMapError => AtlasRealizationError.PartialMap(value)
      case value: CertifiedMapError => AtlasRealizationError.CertifiedMap(value)

  def totalAssignmentError(
      error: TotalMapError | CertifiedMapError
  ): AtlasRealizationError =
    error match
      case value: TotalMapError => AtlasRealizationError.TotalMap(value)
      case value: CertifiedMapError => AtlasRealizationError.CertifiedMap(value)
