# neuroatlas4s

Brain atlas semantics for Scala 3, cross-compiled to the JVM and Scala.js.

| Module | Package | Contents | Depends on |
|---|---|---|---|
| `neuroatlas4s-core` | `neuroatlas4s` | Atlas identities, releases, labels, hierarchies, catalog, realized assignments and runtime-neutral Neuropublish v1 records/validation | locus4s |
| `neuroatlas4s-volume` | `neuroatlas4s` | Exact volume realizations, coordinate queries, parcel/series reductions, overlap, adjacency and volume template routes | core, neuroim4s routing |
| `neuroatlas4s-volume-io` (JVM) | `neuroatlas4s.io` | Schaefer, Glasser, Brainnetome and ASEG loading, asset provenance/cache, inspected TemplateFlow composite admission | volume, neuroim4s IO and transform IO |

The core needs no volume or surface runtime, renderer, or analysis stack.
Membership is a locus4s `PartialSurjection` from a spatial domain onto the
parcel domain; parcel and network regions derive from it, and labels,
networks and display order are typed fields over the parcel domain, so atlas
fields compose directly with locus4s domains, selections and fields. Volume realizations and queries use the optional volume module. Surface
realizations and mixed volume/surface routes remain in ScalaFIM until the
surface extraction. Neither volume module depends on ScalaFIM or a surface or
display runtime. Image formats and coordinate kernels remain in neuroim4s and
its providers; this library supplies atlas admission and mapping policy.

An atlas reference keeps three identities apart: the template space an atlas
is defined in, its sampled resolution or density, and its release. Failures
from a provider an operation delegates to (image geometry, a transform)
arrive as `AtlasError.Provider` with a typed cause supplied by that library.

Persisted spellings (space ids, artifact roles, licence strings, atlas ids)
are unchanged from ScalaFIM, where this code originated.

## Volume use

```scala
import neuroatlas4s.*
import neuroatlas4s.syntax.*
// JVM only:
import neuroatlas4s.io.*
```

`VolumeAtlas.fromLabelVolume` admits a provider-owned categorical grid. Its
`realization.parcelAssignment` is the only membership owner; `labelVolume` is
a derived materialization. Reduction requires the exact live grid owner,
keeps parcel order and uses explicit empty-parcel policy. Queries preserve
nearest-voxel ties and radius ordering. Native IO retains signed label,
affine/scaling and source SHA-256 admission.

`VolumeTransforms` plans anatomical volume template steps over neuroim4s's
`VolumeGraph`; surface endpoints are refused. Routes prefer forward paths,
then reversible inverse paths, and preserve manifest order for ties. Missing
maps and planned steps remain non-executable even for empty requests.
`TemplateGrids` keeps the persistent MNI grid ids. `MniTemplateBridge` admits
only the inspected composite and refuses the misleading reverse file; an
estimated inverse must pass the existing residual gates. `MniTemplateBridgeFiles.loadCached`
takes explicit cache roots; it performs no implicit download.

## Build

Scala 3.7.4, sbt 1.11.7, Node.js for the Scala.js tests.

```sh
sbt neuroatlas4sTestAll
```

Providers are pinned to immutable revisions in `build.sbt`. For coordinated
development, point a provider at a sibling checkout with
`-Dneuroatlas4s.locus4s.build=/absolute/path/to/locus4s`.

## License

Apache-2.0. See [LICENSE](LICENSE).
