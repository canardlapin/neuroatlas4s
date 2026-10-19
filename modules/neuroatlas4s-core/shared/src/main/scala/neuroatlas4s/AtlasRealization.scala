package neuroatlas4s

import locus4s.Bijection
import locus4s.DomainAlignmentError
import locus4s.DomainRegistry
import locus4s.FiniteDomain
import locus4s.Index
import locus4s.PartialSurjection
import locus4s.Region as LocusRegion
import locus4s.Selection
import locus4s.SpaceMismatch
import locus4s.Surjection
import locus4s.data.Field

trait AtlasNetworkAssignment[P]:
  type N
  val networkIds: Field[N, NetworkId]
  val parcelToNetwork: Surjection[P, N]

  def networkPoint(id: NetworkId): Option[Index[N]] =
    networkIds.space.indices.find(index => networkIds(index) == id)

trait AtlasNetworkRealization[X]:
  type N
  val assignment: PartialSurjection[X, N]
  val networkIds: Field[N, NetworkId]

/** One atlas realization over one exact live spatial owner.
  *
  * `parcelAssignment` is the only retained source of membership truth. Parcel
  * regions, network regions, dense labels, and publication assignment payloads
  * are derived from it.
  */
trait AtlasRealization:
  type X
  type P

  val registry: DomainRegistry
  val ref: AtlasRef
  val provenance: AtlasProvenance
  val parcelAssignment: PartialSurjection[X, P]
  val parcelKeys: Field[P, String]
  val metadata: Field[P, AtlasRegionMetadata]
  val displayOrder: Selection[P]
  val networkAssignment: Option[AtlasNetworkAssignment[P]]

  final def parcelDomain: FiniteDomain[P] =
    parcelAssignment.to

  final def regionIds: Field[P, RegionId] =
    metadata.map(_.id)

  final def support: LocusRegion[X] =
    parcelAssignment.support

  final def parcelPoint(id: RegionId): Option[Index[P]] =
    parcelDomain.indices.find(index => metadata(index).id == id)

  final def parcelPointByKey(key: String): Option[Index[P]] =
    parcelDomain.indices.find(index => parcelKeys(index) == key)

  final def region(id: RegionId): Option[LocusRegion[X]] =
    parcelPoint(id).map(parcelAssignment.fiber)

  /** Retarget through exact persistent ordered identity. Equal cardinality is
    * deliberately insufficient.
    */
  final def assignmentAlignedTo[Q](
      target: FiniteDomain[Q]
  ): Either[DomainAlignmentError, PartialSurjection[X, Q]] =
    parcelDomain
      .align(target)
      .map(parcelAssignment.rebindTo)

  /** Retarget a different ordered domain only through a certified bijection. */
  final def assignmentRetargeted[Q](
      bijection: Bijection[P, Q]
  ): Either[SpaceMismatch, PartialSurjection[X, Q]] =
    if parcelDomain.sameRuntimeOwnerAs(bijection.from) then
      Right(parcelAssignment.andThen(bijection.toSurjection))
    else Left(SpaceMismatch.between(parcelDomain, bijection.from))

  final def networkRealization: Option[AtlasNetworkRealization[X]] =
    networkAssignment.map: network =>
      new AtlasNetworkRealization[X]:
        type N = network.N
        val assignment: PartialSurjection[X, N] =
          parcelAssignment.andThen(network.parcelToNetwork)
        val networkIds: Field[N, NetworkId] =
          network.networkIds

  final def networkRegion(id: NetworkId): Option[LocusRegion[X]] =
    networkRealization.flatMap: network =>
      network.networkIds.space.indices
        .find(index => network.networkIds(index) == id)
        .map(network.assignment.fiber)
