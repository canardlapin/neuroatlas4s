package neuroatlas4s

/** An atlas realization with its Neuropublish projection: the optional publication adapter. */
trait PublishedAtlasRealization extends AtlasRealization:
  val parcelDomainRecord: NeuropublishFiniteIndexedDomainV1
  val neuropublishSupportDomains: Vector[NeuropublishSpatialDomainV1]
  val neuropublishAssignments: Vector[NeuropublishHardAssignmentV1]

  final lazy val neuropublishProjection: NeuropublishAtlasProjectionV1 =
    val order =
      displayOrder.indices.map(parcelKeys.apply).toVector
    val hierarchy =
      networkAssignment.map: network =>
        NeuropublishParcelHierarchyV1(
          networkKeys =
            network.networkIds.space.indices
              .map(index => network.networkIds(index).value)
              .toVector,
          parcelToNetworkOrdinals =
            network.parcelToNetwork.toTotalMap.targetOrdinals.toVector
        )
    NeuropublishAtlasProjectionV1(
      atlasProvenance =
        AtlasPublicationProjection.provenance(ref, provenance),
      parcelDomain = parcelDomainRecord,
      parcelMetadata =
        AtlasPublicationProjection.metadata(parcelKeys, metadata),
      displayOrder = order,
      hierarchy = hierarchy,
      supportDomains = neuropublishSupportDomains,
      assignments = neuropublishAssignments
    )

  final def validateNeuropublishProjection(
      projection: NeuropublishAtlasProjectionV1
  ): Either[AtlasPublicationError, Unit] =
    AtlasPublicationProjection.validate(neuropublishProjection, projection)
