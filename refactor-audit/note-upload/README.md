# Note and upload capability batch

The 14-file Scalafix preview changes definitions and callers together. SourceNote takes
TextFiles using a contextual parameter; the interface lives in files, the disk adapter
in server, and Deps supplies it through SeedNoteFiles. SourceNote no longer references
raw filesystem operations. Existing SafePath ordering and lexical confinement remain
unchanged. This does not establish new symlink-confinement guarantees.

HandedFile captures name, size and ByteSource into a Snapshot at the HTTP/tool boundary.
Upload.handed accepts that data and performs no metadata query. All five callers were
rewritten by resolved method symbol, including test callers. ByteSource remains lazy
and repeatable; the snapshot does not eagerly copy the file's contents.

The targeted confirmed scope is three SourceNote filesystem findings and one Upload
metadata finding from capability-treatment/review-1. Upload's getName was already
adjudicated a non-violation; its disappearance is not counted as a fifth repair.
Original finding baselines and classifications remain preserved. No detector or
permission configuration changes are needed for this batch.

`shapes.tsv` records the matched resolved symbols, expressions and ASTs. `plan.tsv`
contains before/after hashes; `applied-verification.txt` verifies all 14 applied files
match the preview exactly. Preview definitions and callers compile together, and full
project compile plus Test/compile passed on the actual module classpaths with SemanticDB.

Frozen test baseline: 22 files-module tests and seven SeedNoteFilesSuite tests, all
passed with no skips. Standalone contracts also passed for injected note reads, OCR
confinement before probes/reads, optional OCR, NUL removal, missing-note failure, disk
reads, metadata queries occurring only at capture, and repeatable upload bytes. A fake
reader records exact calls; a counted File proves domain construction does not query
metadata again, even after the underlying file is removed.

These contracts are development tests, not independent detector precision/recall
evidence. Database-dependent ingestion suites have compiled but were not executed for
this batch. Both after-test comparisons passed: the same 29 identities passed, with
no failures or skips. The integrated assessment completed across 3,138 source/test
files; production capability coverage is 2,368/2,368 files. Raw capability findings
fell from 152 to 147, with no new/reopened anchors or multiplicity changes.

`Completion.scala` reconciles the four confirmed repair identities separately from
the removed legitimate getName call, checks all 14 source hashes, and compares the
before/after test identities and outcomes. Its verified ledger retains the original
review and observation baselines. All other capability identities and multiplicities
must remain unchanged. No new exception or detector-policy change was introduced.

The original capability review's 58 confirmed occurrences in 48 anchors are now
repaired across the agent-storage, plugin-capabilities and note-upload batches;
two separately discovered classloader gaps were repaired by agent-storage. This is
a bounded reviewed subset, not a whole-project automation percentage. The remaining
147 raw production occurrences comprise 146 candidates and one adjudicated
non-violation. Three accepted adapter clock occurrences remain separately tracked.
Independent detector quality and a separate test capability policy remain open.

The primitive inventory increased to 30,436 leaves in 28,248 production member
positions, with 46,867 unresolved leaves across the full inventory. These are
candidates/evidence, not confirmed violations. No modeling repairs are claimed.
The overall migration remains open; see ../next-priority-batch.md.
