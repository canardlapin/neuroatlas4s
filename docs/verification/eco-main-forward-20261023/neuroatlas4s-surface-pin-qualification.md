# neuroatlas4s qualified neurosurf4s default pin

Date: 2026-10-23

## Candidate

`build.sbt` changes the sole default `neurosurf4sRevision` from
`73a244fbe5d7b4db2f0c946d98fa2a3c00101ab7` to qualified provider commit
`4b23386bf750118b9c7c1220b6c592da9ba965e0`. No atlas API, algorithm, or
domain source changed.

The focused gate executed at pin-only commit
`e44dde1ab8e90af7e8fec76a0e73375a08088bb6`. Its author and committer identity
was corrected without changing the tree: amended commit
`0256e98eb78d35523ce6dbd08fea4f19b76e1b68` has the same tree
`64a65a62a6199ad304720e0fb9c22b1dcb8372db`; `git diff e44dde1..0256e98` is
empty. This receipt is committed after that amendment.

## Impact boundary

The mandated workspace catalog command refused a pre-existing catalog-drift
state (uncatalogued repositories/worktrees), so it supplied no usable impact
result and no catalog repair was attempted. Direct build inspection instead
traced the changed provider through:

- `surface{JVM,JS}` -> `neurosurf4s core{JVM,JS}`;
- `surfaceIoJVM` -> `neurosurf4s ioJVM`;
- `routing{JVM,JS}` -> `neurosurf4s routing{JVM,JS}`.

The provider commit is a remote ancestor of published topic
`eco/main-forward-20261023` (head reported as
`ac36debc77d86bbb6108f0f412e890a0a20c28e4` at inspection). Parent-provided
provider qualification is retained under the neurosurf4s topic's
`docs/verification/main-forward-20261023`; this consumer receipt qualifies only
the listed neuroatlas4s dependency edges.

## Executed consumer gates

The source stayed fixed at `e44dde1` for this sequence. JDK 22, external
per-worktree warm state and targets were used.

| Command | Result |
| --- | --- |
| `surfaceJVM/test` | 9 passed, 0 failed, 0 errors |
| `surfaceJS/test` | 9 passed, 0 failed, 0 errors |
| `surfaceIoJVM/test` | 3 passed, 0 failed, 0 errors |
| `routingJVM/test` | 34 passed, 0 failed, 0 errors |
| `routingJS/test` | 34 passed, 0 failed, 0 errors |

The terminal logged wrapper receipt is
`/Volumes/EcoCompileHFS20261023/work/neuroatlas-pin-targets/logs/neuroatlas-pin-02.log.meta.json`:
exit code 0, child return code 0, 336.063730 seconds, started
2026-10-23T17:46:21Z and finished 2026-10-23T17:51:57Z. Its raw log is the
adjacent `neuroatlas-pin-02.log` on the same external volume.

The retained adverse setup receipt is `neuroatlas-pin-01.log` in that directory:
it exited 1 before project loading because the newly-created candidate target
symlinks pointed at external leaf directories that had not yet been created.
Creating those external directories was the only correction; it is not a source
or provider failure.

## Tracker custody correction

An accidental fresh canonical store `st-01M5MXV2Y775QJG82XNQBE20VY` carried
only this actor's superseded issue `bd-01M5MXVF03RF257BBPK74RDZP2`. It was
closed and released, then retained intact at
`/Volumes/EcoCompileHFS20261023/work/neuroatlas4s-accidental-mote-bootstrap-20261023`.
Its complete tar archive has SHA-256
`d709f4f08b3c0a0eae997585d3d0f88e9f639df3203784895f07863d46d30645`; all 22
regular-file hashes and 27 archive members were verified before replacement.

Canonical tracking now reuses the immutable historical Git-versioned lineage
`st-01M5FV72V90JPBYJFH06V5FV85` from the 4df candidate, without copying clone
authority or local state. The replacement issue is
`bd-01M5MYFVKQQKQ56PKCKD2R373N`; its same-lineage immutable operations are
included with this candidate.

## Boundary

These checks establish the listed local JVM/Scala.js consumer contracts at the
new default provider pin. They do not establish hosted CI, a main merge, or a
new scientific/real-data claim.
