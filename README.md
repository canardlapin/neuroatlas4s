# neuroatlas4s

Brain atlas semantics for Scala 3, cross-compiled to the JVM and Scala.js.

| Module | Package | Contents | Depends on |
|---|---|---|---|
| `neuroatlas4s-core` | `neuroatlas4s` | Atlas identities and references (`AtlasRef`, `SpaceId`), releases and provenance (`AtlasProvenance`: sources, licences, citations, derivation), region labels and hierarchy (`RegionIndex`, networks), the standard catalog (`AtlasRegistry`, Schaefer, Glasser, …), and the realized-assignment contract (`AtlasRealization`) | locus4s |

The core needs no volume or surface runtime, renderer, or analysis stack.
Membership is a locus4s `PartialSurjection` from a spatial domain onto the
parcel domain; parcel and network regions derive from it, and labels,
networks and display order are typed fields over the parcel domain, so atlas
fields compose directly with locus4s domains, selections and fields. Volume
and surface realizations, loaders, template routes and publication adapters
live in the libraries that own those runtimes.

An atlas reference keeps three identities apart: the template space an atlas
is defined in, its sampled resolution or density, and its release. Failures
from a provider an operation delegates to (image geometry, a transform)
arrive as `AtlasError.Provider` with a typed cause supplied by that library.

Persisted spellings (space ids, artifact roles, licence strings, atlas ids)
are unchanged from ScalaFIM, where this code originated.

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
