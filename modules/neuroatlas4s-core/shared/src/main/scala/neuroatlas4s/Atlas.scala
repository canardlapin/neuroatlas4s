package neuroatlas4s

trait Atlas:
  def ref: AtlasRef
  def provenance: AtlasProvenance
  def regions: RegionIndex
  def realization: AtlasRealization

  def family: String = ref.family
  def model: String = ref.model
  def name: String = ref.name
  def representation: AtlasRepresentation = ref.representation
