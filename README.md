# neuroatlas4s

Brain atlas semantics for Scala 3 on the JVM and Scala.js. Atlas membership has
one owner: a locus4s partial surjection onto an ordered parcel domain. Geometry
and metadata are retained inputs; parcel regions and label materializations
derive from the assignment.

| Module | Owns | Dependencies |
|---|---|---|
| `core` | Identities, releases, labels, hierarchy, catalog and runtime-neutral publication | locus4s |
| `ioJVM` | Atlas assets, explicit cache policy and file provenance | core, JDK IO |
| `volume` | Volume realizations, queries, reductions, adjacency and volume template policy | core, neuroim4s routing |
| `volumeIoJVM` | Volume atlas loaders and inspected TemplateFlow composite admission | volume, atlas IO, neuroim4s IO |
| `surface` | Bilateral assignments, vertex queries, parcel contacts/distances and topology publication | core, neurosurf4s core |
| `surfaceIoJVM` | GIFTI label atlas admission and file provenance | surface, atlas IO, neurosurf4s IO |
| `routing` | Mixed template manifests, standard surface route policy and explicit sampling plans | surface, volume, neurosurf4s routing |
| `routingIoJVM` | Digest-locked standard route loading | routing, volume/surface IO, neurosurf4s point-map IO |

Core catalogs require no volume or surface runtime. Surface assignments and GIFTI
loading require no volume, transform or rendering runtime. Image formats,
coordinate execution, projection mechanics and rendering remain in their provider
libraries. This library supplies atlas admission, catalog and template policy;
it has no ScalaFIM or MVPA dependency.

All public shared types are in `neuroatlas4s`; JVM file APIs are in
`neuroatlas4s.io`. Select the modules an application needs, then import their
actual types:

```scala
import neuroatlas4s.*
import neuroatlas4s.syntax.* // volume query/reduction syntax
// JVM loaders, when selected:
import neuroatlas4s.io.*
```

`VolumeAtlas.fromLabelVolume` admits the exact live categorical grid owner.
`SurfaceAtlas.fromLabeledSurfaces` admits bilateral labels over surface geometry.
Both expose their single `realization.parcelAssignment`, parcel-indexed metadata
and keys, explicit display order and optional validated network assignment.
Equal cardinality does not establish domain alignment. Surface support identity
includes ordered topology and hemisphere; coordinate changes do not rewrite it.

`AtlasRealizationError` contains runtime-neutral typed admission failures.
`VolumeAtlasRealizationError` retains grid/parcellation causes and wraps the
common error explicitly. These are closed error types; their messages preserve
the original admission detail.

`SpaceTransforms` plans mixed template routes; `VolumeTransforms` accepts volume
endpoints only. Routes prefer forward paths, then qualified inverse paths.
Missing maps and planned steps remain explicitly non-executable. Standard fsLR
routes preserve asset digests, publisher anatomical evidence, hemisphere order,
exact versus approximate mapping and their versioned identity token. Numerical
inverses require the existing residual gates; a filename alone is not evidence
of the direction of a transform. Path-based loaders do not download assets.

Persisted publication bytes, schema names, route tokens and the existing
`scalafim/atlas` cache location retain their original spelling.

## Checks

Scala 3.7.4, sbt 1.11.7 and Node.js for Scala.js tests.

```sh
sbt coreJVM/test coreJS/test ioJVM/test surfaceJVM/test surfaceJS/test surfaceIoJVM/test
sbt volumeJVM/test volumeJS/test volumeIoJVM/test
sbt routingJVM/test routingJS/test routingIoJVM/test
```

The native MNI inverse court uses a separate 3 GiB JVM. Real-asset courts skip
when assets are absent; `SCALAFIM_REQUIRE_REAL_ASSETS=1` requires them.
`SCALAFIM_TIMING_BUDGETS=1` separately enables frozen timing assertions.
A passing default run does not establish a timing budget or hosted CI result.

Providers use immutable source revisions. A deliberate local override such as
`-Dneuroatlas4s.neurosurf4s.build=/absolute/path` selects that checkout. Explicit
overrides for its shared dependencies are forwarded separately.
No provider needs this repository to build.

Apache-2.0. See [LICENSE](LICENSE).
