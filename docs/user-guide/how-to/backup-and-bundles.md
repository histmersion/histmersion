# Backups, exports and moving a collection

Your collection lives in one database on your computer. Keep copies:

| you want | use | what you get |
|---|---|---|
| to recover from a disaster | `histmersion backup` | a full database dump: everything, restorable byte for byte |
| a readable copy, for git or a notes app | `histmersion export <dir>` | one Markdown file per source and per article |
| to carry a collection to another machine, and merge it there | `histmersion bundle export` | a `.hmx` bundle, merged with `histmersion merge …` |

Originals of books stay in Calibre, which Histmersion never owns ([Books and Calibre](books-and-calibre.md)).

## The readable export

```bash
histmersion export wiki                 # sources → wiki/sources/<citekey>.md, articles → wiki/<kind>/<slug>.md,
                                        # bookkeeping and goals → wiki/meta/
histmersion export wiki --no-meta       # without the bookkeeping
histmersion queue export meta           # only the bookkeeping: ACQUIRE.md, TASKS.md, LOG.md, GOALS.md
```

Pass the parent folder (`wiki`), not `wiki/sources` — the export makes the subfolders itself. The
export is a copy: edits to these files do not go back into the database. `histmersion seed <dir>`
can import such a tree into an empty collection (metadata, summaries, findings, notes, articles —
not the full texts, which `ingest` re-creates from the originals).

## Backups

The dump is the backup; the export is the archive you can read. Keep both.

### Taking a backup

`pg_dump` runs **on the server**, not in the CLI: the CLI is a pure HTTP client and holds no
database credentials (the bundled Postgres listens on loopback only). The archive is `pg_dump -Fc`
(custom format, compressed) and lands in the server's `backups.dir` — `<data>/backups` in the
bundle (`~/Library/Application Support/Histmersion/backups` on macOS,
`~/.local/share/histmersion/backups` on Linux). After each dump only the newest `backups.keep`
archives stay.

```bash
histmersion backup                      # dump on the server; prints file, size, sha256, duration
histmersion backup --download .         # …and stream a copy into the current directory
histmersion backups                     # what is on the server, newest first
```

Names are `histmersion-<UTC timestamp>.dump` (e.g. `histmersion-20260825T145112Z.dump`), so they sort
chronologically. The dump is written to a `.part` file and moved into place only on success, so a
listing never shows a half-written archive. `backup` is also an **MCP tool** — an agent can save the
state before a bulk reseed. There is deliberately no `restore` MCP tool: it destroys data.

### Restoring

```bash
histmersion restore histmersion-20260825T145112Z.dump --force     # asks for confirmation unless --yes
```

- The file must be one the server lists — no paths, no `..`; the name is validated against
  `GET /api/backups` before it is opened.
- Restoring **drops what is there** (`pg_restore --clean --if-exists`), so it refuses to run while the
  database still holds sources unless you pass `--force`. An empty database needs no flag.
- While it runs, the server holds a **maintenance lock**: every write endpoint (`seed`, `put`,
  `patch`, `import`, `reindex`, `edges/resolve`, `backup`) answers **503** until it finishes. Reads
  keep working.
- Afterwards the server re-runs **Flyway validate** and reports the schema version, so a dump from an
  older schema cannot pass silently.
- `pg_restore` exits non-zero when it *skipped* a statement but otherwise completed (see the version
  note below); those lines come back as `warnings` rather than failing the restore.

To restore into a completely fresh database, set the old data directory aside and let the server
create a new one. On macOS (`<data>` = `~/Library/Application Support/Histmersion`; Linux:
`~/.local/share/histmersion`):

```bash
just stop                                             # the server stops Postgres and the converter with it
mv "$HOME/Library/Application Support/Histmersion" "$HOME/Library/Application Support/Histmersion.old"
mkdir -p "$HOME/Library/Application Support/Histmersion/logs"    # the service writes its log here
just start                                            # first start: initdb, then Flyway builds an empty schema
cp "$HOME/Library/Application Support/Histmersion.old/backups/histmersion-20260825T145112Z.dump" \
   "$HOME/Library/Application Support/Histmersion/backups/"
histmersion restore histmersion-20260825T145112Z.dump  # an empty database needs no --force
```

Delete `Histmersion.old` once the restored corpus looks right.

### The endpoints and the config

| method | path | body | returns |
|---|---|---|---|
| `POST` | `/api/backup` | — | `BackupResult{file, bytes, sha256, durationMs}` |
| `GET` | `/api/backups` | — | `[BackupInfo{file, bytes, createdAt}]`, newest first |
| `GET` | `/api/backups/{file}` | — | the archive itself, streamed |
| `POST` | `/api/maintenance/vacuum` | `VacuumRequest{analyze, tables[]}` | `JobInfo` of a `vacuum` job (follow it with `GET /api/jobs/{id}`); `tables` empty = `maintenance.vacuum-tables` |
| `POST` | `/api/restore` | `RestoreRequest{file, force}` | `RestoreResult{file, schemaVersion, sourcesBefore, sourcesAfter, durationMs, warnings[]}` |

| key | env | default | meaning |
|---|---|---|---|
| `backups.dir` | `HISTMERSION_BACKUPS_DIR` | `backups` (bundle: `<data>/backups`) | server-side directory for the archives |
| `backups.pg-bin-dir` | `HISTMERSION_PG_BIN_DIR` | `` (use `PATH`; bundle: `<home>/pg/bin`) | directory holding `pg_dump`/`pg_restore` when they are off `PATH` |
| `backups.keep` | `HISTMERSION_BACKUPS_KEEP` | `5` | archives kept after a dump, newest first; `0` keeps every one |
| `backups.timeout-seconds` | — | `1800` | a dump/restore that outlives this is killed rather than hanging the request |
| `maintenance.vacuum-tables` | `HISTMERSION_MAINTENANCE_VACUUM_TABLES` | `places,place_names,place_links,place_descriptions,place_footprints,place_relations,finding_places,findings` | what `histmersion maintenance vacuum` covers when no `--table` is named |


### `pg_dump` has to be there, and its version matters

The bundle carries **`pg_dump`/`pg_restore` 18** in `<home>/pg/bin`, beside the Postgres 18 they
dump, and the server uses them without configuration. `sbt server/run` without the bundle runtime
needs the client tools locally:

```bash
brew install libpq                                   # macOS: keg-only, so not on PATH
export HISTMERSION_PG_BIN_DIR=/opt/homebrew/opt/libpq/bin  # …which is what this points at
```

If they are missing, `histmersion backup` says so and does nothing else. Mind the **major version**:
homebrew's libpq is newer than the server, and a `pg_restore` newer than the target Postgres emits
`SET transaction_timeout`, which Postgres < 17 rejects — the data restores fine, and the skipped
statement is reported as a warning. Matching versions (as in the bundle) produce none.

The DB-gated integration test (`com.histmersion.server.BackupRestoreSuite`) exercises exactly this path —
backup, download, drop a source, `restore --force`, find it again — and skips with a reason when
`HISTMERSION_TEST_DB_URL` or the client tools are absent.

Manual escape hatch, if the server itself will not start: stop the service, start the cluster by
hand with the bundle's own tools, dump it, stop it again (macOS paths; on Linux the bundle is
`~/.local/opt/histmersion` and the data `~/.local/share/histmersion`):

```bash
just stop
pg="$HOME/Applications/Histmersion/pg/bin"; data="$HOME/Library/Application Support/Histmersion"
"$pg/pg_ctl" -D "$data/pg" -l "$data/logs/postgres.log" -w \
  -o "-p 5433 -c listen_addresses=127.0.0.1 -c unix_socket_directories=''" start
PGPASSWORD=histmersion "$pg/pg_dump" -h 127.0.0.1 -p 5433 -U histmersion -d histmersion -Fc -f histmersion.dump
"$pg/pg_ctl" -D "$data/pg" -m fast -w stop
```

The password is `db.password` (`HISTMERSION_DB_PASS`, default `histmersion`), which the cluster was
initialised with.

## Portable bundles

A third way to carry a collection, built to be **merged** rather than restored
([`docs/research-merge-import.md`](https://github.com/histmersion/histmersion/blob/master/docs/research-merge-import.md)): a `.hmx` bundle is a lossless,
logical export of the database content — sources with their OCR bodies and stored originals,
findings, edges, assessments, aliases, articles with their versions and old slugs, assets, asset
links, attestations, events (with what the catalogues say about them, the sources proposed or decided as
discussing them, and the corrections made by hand), goals (with their approved plan, run cap, links and
schedules), the queues, the places and named periods this collection added itself, the site types
refined for any place, and the tombstones of what was deleted — that keeps every row's **identity**. Calibre is not in it: `calibre_id` is a per-machine pointer, and a merged
instance rebuilds its Calibre library from the stored originals with `calibre sync --apply`.

```bash
histmersion bundle instance --label work           # name this collection (every row records the instance that made it)
histmersion bundle export work.hmx                 # everything; deterministic: an unchanged database gives the same bytes
histmersion bundle export delta.hmx --since 2026-10-03T09:12:00.123456Z   # what changed after a previous bundle's asOf
histmersion bundle verify work.hmx                 # format, schema, checksums, revs, blobs — every problem listed
histmersion merge plan work.hmx                    # every match and every issue, in one run (writes nothing)
histmersion merge plan work.hmx --write-template r.yaml   # a resolution file listing every issue
histmersion merge plan work.hmx --resolutions r.yaml --resolve S-3c1e7a2b=keep-left   # what remains
histmersion bundle import work.hmx --dry-run       # what an import would do
histmersion bundle import work.hmx --resolutions r.yaml   # one transaction; refused (nothing written) on any problem or open issue
histmersion merge apply work.hmx --resolutions r.yaml --plan 7c41e9a2   # the reviewed plan, folds included: a backup, then one transaction
histmersion merge list                             # what was merged, from where, with which plan and backup
histmersion merge show 0193f1c4                    # one merge's report, resolutions and remembered decisions
histmersion merge ledger --uid guterbock1997_hittite   # where a record came from; the merged-away uids that redirect to it
```

The same review on a screen: **Library › Merge another collection** in the web UI (`/library/merge`) uploads a bundle,
shows its plan — the issues grouped, open ones first, with a counter — and decides each in place: a
source pair as a field table (here | result | bundle) with the score's evidence, an article as a
three-way view (here | common version | bundle) with an editor for the merged text, a place or an
event as both geometries on a map and both spans on one axis. Each decision is kept on the server
(`merge_sessions`, so a reload or another browser carries on) and the plan is made again at once;
the decisions download as the resolution file the CLI reads (a merged text goes along in its
`texts:`), and one written by the CLI uploads. *Apply* sits behind the write key and a
confirmation, posts the plan hash just reviewed and follows the apply job — its backup first — to the
report.

- **Identity.** Every row a bundle carries has a `uid` (UUIDv7) and an `origin` (the instance that
  created it); a citekey or slug is only a label — `rename` keeps the uid. A delete, cascades
  included, leaves a tombstone, so a deletion travels too.
- **The file.** A gzip'd tar: `manifest.json` (format and schema version, the exporting instance,
  `since`/`asOf`, a checksum per file, a fingerprint of the gazetteer and periods), one NDJSON file
  per kind (one canonical JSON line per row, with a `rev` = the sha256 of its content), and
  content-addressed `blobs/sha256/…` for OCR bodies, version bodies, asset bytes and originals,
  streamed so the size of a bundle never matters to memory. The gazetteer and the named periods every
  instance seeds from the same files (`histmersion init`, `places seed|fetch`, `periods seed|fetch`:
  `dataset = seed`) are not carried; what a person added (`places import`, `periods import`: `dataset =
  local`, the default) is, and merges like any record — one id with the same geometry and span is one
  place, a different one is a `place-differs` question (keep-left | keep-right) shown on a map; a place
  under another id with the same label and kind, and points within `merge.place-within-km` or shapes
  overlapping by `merge.place-overlap-at-least`, is one too, and the bundle's events follow it. Places
  and periods that existed before V052 count as seeds: re-import a file of your own with `--dataset
  local` to have it travel.
- **Import.** A bundle row is the local row with the same uid (or the one a redirect or the merge
  ledger maps it to), or a new one. Into an empty instance that is a copy (export → import → export
  gives the same bytes but for the manifest's instance); into the instance a bundle came from it
  changes nothing. Between two instances that share rows, the merge ledger remembers what was
  imported, so a row changed only over there is taken and a row changed only here is kept. A row
  deleted here is not brought back.
- **The merge plan.** `merge plan` matches the bundle's other sources against this collection's —
  a strong key (the same DOI, original file or ISBN) decides; else a weighted score over title,
  authors (name forms, order and diacritics folded), year, venue, OCR body (MinHash) and old
  citekeys — and lists **every** issue in one run, each with a stable id: possibly the same work
  (`map:<citekey>` or `both`), one citekey for two works, fields that differ in a pair to be merged
  (DOI, year, status, title, authors, summary, notes, period, OCR body), rows changed on both sides
  or deleted there and changed here, articles that diverged, labels a different record has, old
  citekeys that name a different source. An issue that exists only under one answer to another is
  listed as conditional (`if S-… = map`). Decisions go in a resolution file (`--write-template`
  starts one); an id whose records changed is reported as stale, never re-applied. `bundle import`
  refuses while an issue is open, and carries out what it can (`keep-left`/`keep-right` on a
  conflict, `keep-left` on a taken label, `both`). Left is always this collection, right the bundle.
  The matcher's weights and thresholds are `merge.*`.
- **What a merged pair owns.** Inside a pair of sources that are one, the findings are matched (the
  same claim, time and place fold into one, tags unioned and the more cautious confidence kept; a
  near duplicate is kept beside it unless decided `merge`; the rest are numbered after the
  survivor's last), figures with the same bytes are one (a taken name gets the other collection's
  label), and attestations are re-anchored by their quote, prefix and suffix into the OCR body the
  merge keeps — one that cannot be is a blocking `drop | keep-unanchored` question, under each
  alternative of the body question. Edges are re-pointed through the merge (a self-loop dropped).
  An article both sides changed merges three-way against the newest version both histories hold
  (clean: automatic and queued for review; conflicting: `keep-left | keep-right | both | file:`), or
  fast-forwards when one text is a version of the other; `[[citekey]]` links in the bundle's articles
  follow the merge. A goal, a queue item (`acquire`/`tasks`, same text) or an event dated alike on both
  sides is one — and so are two events under different ids with the same label, dates sharing a year
  and the same place. A goal that is one keeps the links of both and this collection's schedules (a
  difference is reported; a schedule brought in by a merge joins the goal's watcher, created paused,
  which keeps its own cron),
  and takes the plan approved later — two plans and neither known to be newer is a
  `goal-plan-differs` question. A rename made over there (an article slug, an event id, a goal slug, as
  a citekey) is followed; an old slug, event id, goal slug or place id keeps resolving (`article
  rename`, `events rename`, `goals rename`, `places rename` keep the alias too), and the old labels
  travel — one that names another record here is left out and said.
- **Apply.** `merge apply` plans again and refuses unless nothing is open and the plan is the one
  reviewed (`--plan <hash>`); then takes a backup (`histmersion backup`'s `pg_dump`), and in one
  transaction writes the merge — the bundle's uid of every folded record a redirect to the survivor,
  its citekey an old citekey of it, the ledger, a `/review-article` task per merged article, a `log`
  entry — and plans once more: the report says the merge left 0 changes. Applying the same bundle
  again changes nothing; every decision carried out is remembered, so the next plan of an unchanged
  record does not ask again. The apply runs as a background job (`GET /api/merge/apply/{job}`; the CLI
  follows it), so a large collection's backup does not time the request out. Not an MCP tool:
  applying a merge is a person's call, like a restore.

| key | env | default | meaning |
|---|---|---|---|
| `bundles.dir` | `HISTMERSION_BUNDLES_DIR` | `bundles` (bundle: `<data>/bundles`) | server-side scratch where an export is built before its download |
| `bundles.stale-minutes` | `HISTMERSION_BUNDLES_STALE_MINUTES` | `60` | a finished export — or an uploaded bundle not planned or imported since — older than this is deleted by the next export or upload |
| `merge.auto-merge` | `HISTMERSION_MERGE_AUTO` | `14` | a score at or above this, with no hard flag (two DOIs, years more than one apart), merges without asking |
| `merge.new-at-most` | `HISTMERSION_MERGE_NEW_AT_MOST` | `4` | a score at or below this is a different work; in between, the user decides |
| `merge.minhash-size` | `HISTMERSION_MERGE_MINHASH_SIZE` | `64` | hash functions per OCR-body sketch |
| `merge.finding-near-at-least` | `HISTMERSION_MERGE_FINDING_NEAR_AT_LEAST` | `0.6` | two findings of a merged pair whose claim words overlap this much (Jaccard, periods overlapping) are near-duplicates: kept apart unless the user says `merge` |
| `merge.finding-within-km` | `HISTMERSION_MERGE_FINDING_WITHIN_KM` | `50` | two findings of a merged pair this close are at the same place |
| `merge.place-within-km` | `HISTMERSION_MERGE_PLACE_WITHIN_KM` | `5` | a bundle's place with no id here is the same as a place here with its label and kind when the two points are this close |
| `merge.place-overlap-at-least` | `HISTMERSION_MERGE_PLACE_OVERLAP_AT_LEAST` | `0.8` | …or when the two shapes overlap this much (intersection over union) |
| `merge.apply-wait-seconds` | `HISTMERSION_MERGE_APPLY_WAIT_SECONDS` | `20` | how long `POST /api/merge/apply` waits before it answers `running` with the job to follow |
| `merge.weights.*` | `HISTMERSION_MERGE_W_*` | see `reference.conf` | the score's weight per field comparison (title, authors, year, venue, body, old citekey) |
