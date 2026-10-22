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

enum VolumeAtlasRealizationError:
  case Realization(cause: AtlasRealizationError)
  case GridDomain(cause: GridDomainError)
  case Parcellation(cause: VolumeParcellationError)

  def message: String =
    this match
      case Realization(cause) => cause.message
      case GridDomain(cause) => cause.message
      case Parcellation(cause) => cause.message

object AtlasRealizations:
  import AtlasRealizationAdmission.*

  /** Register the image's exact grid owner and build one direct volume
    * realization from a categorical image4s-backed label volume.
    */
  def volumeFromLabelsIn(
      registry: DomainRegistry,
      atlasRef: AtlasRef,
      regions: RegionIndex,
      labels: SomeLabelVolume[Int],
      atlasProvenance: AtlasProvenance
  ): Either[VolumeAtlasRealizationError, VolumeAtlasRealization] =
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
  ): Either[VolumeAtlasRealizationError, VolumeAtlasRealization] =
    GridDomain
      .register(
        SomeNeuroVolume.sampled(labels).grid,
        s"${atlasRef.name} voxels",
        registry
      )
      .left
      .map(VolumeAtlasRealizationError.GridDomain.apply)
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
  ): Either[VolumeAtlasRealizationError, VolumeAtlasRealization] =
    for
      _ <- validateProvenanceCoherence(
        atlasRef,
        regions,
        atlasProvenance,
        AtlasRepresentation.Volume
      ).left.map(VolumeAtlasRealizationError.Realization.apply)
      labelField <- gridDomain
        .spatialField(SomeNeuroVolume.sampled(labels))
        .left
        .map(VolumeAtlasRealizationError.GridDomain.apply)
      parcelResolution <- AtlasParcelDomain
        .restore(registry, atlasRef, atlasProvenance, regions)
        .left
        .map(error => VolumeAtlasRealizationError.Realization(AtlasRealizationError.Publication(error)))
      assignments <- volumeAssignments(
        gridDomain.space,
        labelField,
        regions.ids.zipWithIndex.toMap
      ).left.map(VolumeAtlasRealizationError.Realization.apply)
      _ <- validateCoverage(assignments, regions).left.map(VolumeAtlasRealizationError.Realization.apply)
      assignmentValue <- PartialSurjection
        .fromOptionalTargetOrdinals(
          gridDomain.space,
          parcelResolution.space,
          assignments
        )
        .left
        .map(error => VolumeAtlasRealizationError.Realization(partialAssignmentError(error)))
      metadataField <- VectorField
        .fromValues(parcelResolution.space, regions.regions)
        .left
        .map(error => VolumeAtlasRealizationError.Realization(AtlasRealizationError.Field(error)))
      parcellationValue <- VolumeParcellation
        .create(
          gridDomain,
          assignmentValue,
          metadataField,
          SomeNeuroVolume.sampled(labels).metadata
        )
        .left
        .map(VolumeAtlasRealizationError.Parcellation.apply)
      displayOrderValue <- Selection
        .fromOrdinals(parcelResolution.space, regions.regions.indices)
        .left
        .map(error => VolumeAtlasRealizationError.Realization(AtlasRealizationError.Selection(error)))
      networkResult <- networkAssignment(
        parcelResolution.registry,
        parcelResolution.space,
        regions
      ).left.map(VolumeAtlasRealizationError.Realization.apply)
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
