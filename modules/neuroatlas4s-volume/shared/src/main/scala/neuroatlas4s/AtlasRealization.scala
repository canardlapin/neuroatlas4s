package neuroatlas4s

import neuroatlas4s.*

import image4s.geometry.D3
import image4s.geometry.Frame
import image4s.locus.GridDomain
import image4s.locus.GridDomainError
import locus4s.Bijection
import locus4s.CertifiedMapError
import locus4s.DomainAlignmentError
import locus4s.DomainError
import locus4s.DomainRecord
import locus4s.DomainRegistry
import locus4s.DomainRestoreError
import locus4s.FiniteDomain
import locus4s.FiniteSpace
import locus4s.Index
import locus4s.PartialMapError
import locus4s.PartialSurjection
import locus4s.Region as LocusRegion
import locus4s.Selection
import locus4s.SelectionError
import locus4s.SpaceMismatch
import locus4s.Surjection
import locus4s.TotalMapError
import locus4s.data.Field
import locus4s.data.FieldConstructionError
import locus4s.data.VectorField
import neuroim4s.image.SomeNeuroVolume
import neuroim4s.image.SomeLabelVolume
import neuroim4s.image.VolumeParcellation
import neuroim4s.image.VolumeParcellationError

trait VolumeAtlasRealization extends PublishedAtlasRealization:
  type F <: Frame[D3]

  val domain: GridDomain[F, D3, X]
  val parcellation: VolumeParcellation[F, X, P, AtlasRegionMetadata]
  val volumeSupportDomain: NeuropublishVolumeGridDomainV1

enum AtlasRealizationError:
  case InvalidAtlas(error: AtlasError)
  case GridDomain(error: GridDomainError)
  case InvalidDomain(error: DomainError)
  case DomainRestore(error: DomainRestoreError)
  case PartialMap(error: PartialMapError)
  case TotalMap(error: TotalMapError)
  case CertifiedMap(error: CertifiedMapError)
  case Field(error: FieldConstructionError)
  case Selection(error: SelectionError)
  case Parcellation(error: VolumeParcellationError)
  case Publication(error: AtlasPublicationError)

  def message: String =
    this match
      case InvalidAtlas(error) => error.message
      case GridDomain(error) => error.message
      case InvalidDomain(error) => error.message
      case DomainRestore(error) => error.message
      case PartialMap(error) => error.message
      case TotalMap(error) => error.message
      case CertifiedMap(error) => error.message
      case Field(error) => error.message
      case Selection(error) => error.message
      case Parcellation(error) => error.message
      case Publication(error) => error.message

object AtlasRealizations:
  /** Register the image's exact grid owner and build one direct volume
    * realization from a categorical image4s-backed label volume.
    */
  def volumeFromLabelsIn(
      registry: DomainRegistry,
      atlasRef: AtlasRef,
      regions: RegionIndex,
      labels: SomeLabelVolume[Int],
      atlasProvenance: AtlasProvenance
  ): Either[AtlasRealizationError, VolumeAtlasRealization] =
    volumeFromImageIn(
      registry,
      atlasRef,
      regions,
      labels,
      atlasProvenance
    )

  private[neuroatlas4s] def volumeFromImageIn(
      registry: DomainRegistry,
      atlasRef: AtlasRef,
      regions: RegionIndex,
      labels: SomeLabelVolume[Int],
      atlasProvenance: AtlasProvenance
  ): Either[AtlasRealizationError, VolumeAtlasRealization] =
    GridDomain
      .register(
        SomeNeuroVolume.sampled(labels).grid,
        s"${atlasRef.name} voxels",
        registry
      )
      .left
      .map(AtlasRealizationError.GridDomain.apply)
      .flatMap: resolution =>
        volumeIn(
          resolution.registry,
          atlasRef,
          regions,
          resolution.value,
          labels,
          atlasProvenance
        )

  def volumeFromLabels(
      atlasRef: AtlasRef,
      regions: RegionIndex,
      labels: SomeLabelVolume[Int],
      atlasProvenance: AtlasProvenance
  ): VolumeAtlasRealization =
    volumeFromLabelsIn(
      DomainRegistry.empty,
      atlasRef,
      regions,
      labels,
      atlasProvenance
    ).fold(error => throw new IllegalArgumentException(error.message), value => value)

  /** Build against a caller-owned exact grid domain. The image must share the
    * same live grid owner; serialized geometry equality is not enough.
    */
  def volumeIn[F0 <: Frame[D3], S](
      registry: DomainRegistry,
      atlasRef: AtlasRef,
      regions: RegionIndex,
      gridDomain: GridDomain[F0, D3, S],
      labels: SomeLabelVolume[Int],
      atlasProvenance: AtlasProvenance
  ): Either[AtlasRealizationError, VolumeAtlasRealization] =
    for
      _ <- validateProvenanceCoherence(
        atlasRef,
        regions,
        atlasProvenance,
        AtlasRepresentation.Volume
      )
      labelField <- gridDomain
        .spatialField(SomeNeuroVolume.sampled(labels))
        .left
        .map(AtlasRealizationError.GridDomain.apply)
      parcelResolution <- AtlasParcelDomain
        .restore(registry, atlasRef, atlasProvenance, regions)
        .left
        .map(AtlasRealizationError.Publication.apply)
      assignments <- volumeAssignments(
        gridDomain.space,
        labelField,
        regions.ids.zipWithIndex.toMap
      )
      _ <- validateVolumeCoverage(assignments, regions)
      assignmentValue <- PartialSurjection
        .fromOptionalTargetOrdinals(
          gridDomain.space,
          parcelResolution.space,
          assignments
        )
        .left
        .map(partialAssignmentError)
      metadataField <- VectorField
        .fromValues(parcelResolution.space, regions.regions)
        .left
        .map(AtlasRealizationError.Field.apply)
      parcellationValue <- VolumeParcellation
        .create(
          gridDomain,
          assignmentValue,
          metadataField,
          SomeNeuroVolume.sampled(labels).metadata
        )
        .left
        .map(AtlasRealizationError.Parcellation.apply)
      displayOrderValue <- Selection
        .fromOrdinals(parcelResolution.space, regions.regions.indices)
        .left
        .map(AtlasRealizationError.Selection.apply)
      networkResult <- networkAssignment(
        parcelResolution.registry,
        parcelResolution.space,
        regions
      )
    yield
      type Parcel = parcelResolution.P
      val volumeSupport =
        VolumePublication.volumeDomain(
          atlasRef.coordSpace.value,
          gridDomain.record
        )
      val publicationAssignment =
        NeuropublishHardAssignmentV1(
          localId = "volume-hard-assignment",
          source = volumeSupport.identity,
          target = parcelResolution.publication.identity,
          targetOrdinals = assignments.map(_.getOrElse(-1)),
          coverage = NeuropublishTargetCoverageV1.Complete,
          provenance =
            AtlasPublicationProjection.assignmentProvenance(
              regions,
              parcelResolution.publication
            )
        )
      new VolumeAtlasRealization:
        type F = F0
        type X = S
        type P = Parcel
        val registry: DomainRegistry = networkResult._1
        val ref: AtlasRef = atlasRef
        val provenance: AtlasProvenance = atlasProvenance
        val domain: GridDomain[F0, D3, S] = gridDomain
        val parcellation: VolumeParcellation[
          F0,
          S,
          Parcel,
          AtlasRegionMetadata
        ] = parcellationValue
        val parcelAssignment: PartialSurjection[S, Parcel] =
          parcellationValue.assignment
        val parcelKeys: Field[Parcel, String] =
          parcelResolution.keys
        val metadata: Field[Parcel, AtlasRegionMetadata] =
          parcellationValue.parcelMetadata
        val displayOrder: Selection[Parcel] = displayOrderValue
        val networkAssignment: Option[AtlasNetworkAssignment[Parcel]] =
          networkResult._2
        val parcelDomainRecord: NeuropublishFiniteIndexedDomainV1 =
          parcelResolution.publication
        val volumeSupportDomain: NeuropublishVolumeGridDomainV1 =
          volumeSupport
        val neuropublishSupportDomains: Vector[NeuropublishSpatialDomainV1] =
          Vector(volumeSupport)
        val neuropublishAssignments: Vector[NeuropublishHardAssignmentV1] =
          Vector(publicationAssignment)

  def validateVolumeCoverage(
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

  private def volumeAssignments[S](
      space: FiniteDomain[S],
      labels: Field[S, Int],
      parcelOrdinalById: Map[RegionId, Int]
  ): Either[AtlasRealizationError, Vector[Option[Int]]] =
    val assignments = Array.fill[Option[Int]](space.size)(None)
    var failure = Option.empty[AtlasRealizationError]
    space.foreachIndex: index =>
      if failure.isEmpty then
        val label = labels(index)
        if label < 0 then
          failure = Some(
            AtlasRealizationError.InvalidAtlas(
              AtlasError.InvalidRegionMetadata(
                s"atlas volume contains negative region id $label"
              )
            )
          )
        else if label != 0 then
          parcelOrdinalById.get(RegionId(label)) match
            case Some(parcelOrdinal) =>
              assignments(index.ordinal) = Some(parcelOrdinal)
            case None =>
              failure = Some(
                AtlasRealizationError.InvalidAtlas(
                  AtlasError.MissingRegionId(RegionId(label))
                )
              )
    failure.toLeft(assignments.toVector)

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
