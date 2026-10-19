package neuroatlas4s

import locus4s.{DomainRecord, DomainRegistry, PartialSurjection, Selection, Surjection}
import locus4s.data.VectorField

/** The realization contract over plain locus4s domains: no volume or surface runtime. */
class AtlasRealizationContractSuite extends munit.FunSuite:
  private def ok[E, A](result: Either[E, A]): A =
    result.fold(error => fail(s"unexpected failure: $error"), identity)

  private val regions =
    RegionIndex(
      Vector(
        AtlasRegionMetadata(RegionId(7), "A", network = Some(NetworkId("visual"))),
        AtlasRegionMetadata(RegionId(9), "B", network = Some(NetworkId("motor")))
      )
    )

  private val atlasRef =
    AtlasRef.volume(
      family = "toy",
      model = "ContractAtlas",
      templateSpace = SpaceId.Custom,
      coordSpace = SpaceId.MNI152,
      confidence = Confidence.Exact
    )

  private def realize(registry: DomainRegistry): AtlasRealization =
    val ambient = ok(registry.restore(ok(DomainRecord.parse("contract:ambient", "contract ambient", 4))))
    val parcels = ok(ambient.registry.restore(ok(DomainRecord.parse("contract:parcels", "contract parcels", 2))))
    val networks = ok(parcels.registry.restore(ok(DomainRecord.parse("contract:networks", "contract networks", 2))))
    val assignment = ok(
      PartialSurjection.fromOptionalTargetOrdinals(ambient.space, parcels.space, Vector(Some(0), Some(1), None, Some(0)))
    )
    val keys = ok(VectorField.fromValues(parcels.space, Vector("a", "b")))
    val metadataField = ok(VectorField.fromValues(parcels.space, regions.regions))
    val order = ok(Selection.fromOrdinals(parcels.space, Vector(1, 0)))
    val toNetwork = ok(Surjection.fromTargetOrdinals(parcels.space, networks.space, Vector(0, 1)))
    val networkIdField = ok(VectorField.fromValues(networks.space, Vector(NetworkId("visual"), NetworkId("motor"))))
    val network =
      new AtlasNetworkAssignment[parcels.S]:
        type N = networks.S
        val networkIds = networkIdField
        val parcelToNetwork = toNetwork
    new AtlasRealization:
      type X = ambient.S
      type P = parcels.S
      val registry = networks.registry
      val ref = atlasRef
      val provenance = AtlasProvenance.fromRef(atlasRef, regions)
      val parcelAssignment = assignment
      val parcelKeys = keys
      val metadata = metadataField
      val displayOrder = order
      val networkAssignment = Some(network)

  test("regions, keys and networks derive from the one parcel assignment"):
    val realization = realize(DomainRegistry.empty)
    val a = realization.region(RegionId(7)).get
    assertEquals(a.ordinalsInDomainOrder.toVector, Vector(0, 3))
    assertEquals(realization.region(RegionId(9)).get.ordinalsInDomainOrder.toVector, Vector(1))
    assertEquals(realization.region(RegionId(8)), None)
    assertEquals(realization.parcelPointByKey("b").map(realization.metadata(_).id), Some(RegionId(9)))
    assertEquals(realization.support.ordinalsInDomainOrder.toVector, Vector(0, 1, 3))
    assertEquals(realization.networkRegion(NetworkId("visual")).map(_.ordinalsInDomainOrder.toVector), Some(Vector(0, 3)))

  test("assignment retargets only to the same persistent parcel domain"):
    val realization = realize(DomainRegistry.empty)
    assert(realization.assignmentAlignedTo(realization.parcelDomain).isRight)
    val foreign = ok(DomainRegistry.empty.restore(ok(DomainRecord.parse("other:parcels", "other parcels", 2)))).space
    assert(realization.assignmentAlignedTo(foreign).isLeft)
