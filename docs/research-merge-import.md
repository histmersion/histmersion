# Research: merging two Histmersion collections (portable export + dedup merge-import)

**Status:** research and design proposal (2026-10-03). **Milestones M0–M7 are built** (identity,
`bundle export|verify`, import without matching; the source matcher, `merge plan`, resolution files
and stable issue ids; the dependent entities of matched pairs and `merge apply`; the web `/merge`
review screen; `merge list|show|ledger`, the MCP `merges` tool, docs and skills), and so are the
extras M4–M5 left open (a collection's own places and periods, events across two ids, label renames
with aliases, old article slugs, goals' plans/links/schedules, the apply as a job). Where the build
departed from the proposal, §7.5 (M0–M2), §7.6 (M3), §7.7 (M4–M5) and §7.8 (M6–M7) say how and why,
and the affected paragraphs carry an **(as built)** note. The
figures describe the live corpus on that date, read with `histmersion query`. Statements I could not
check are marked **(unverified)**.

## 0. The problem in one paragraph

Someone keeps one Histmersion at work and another at home. Both start from the same bundled seeds
(Pleiades/Cliopatria/Natural Earth places, PeriodO presets), and both then grow on their own: sources,
OCR bodies, findings, typed edges, articles with version history, assets, events, attestations, goals,
the three queues and rename aliases. Later the user wants **one** collection. The same
work will have been added on both sides under different citekeys, with author lists in different
forms, different years, a DOI on one side only, or a different scan. The merge has to find those
duplicates, fold them together where that is safe, and **stop** where it is not. It also has to report
**every** problem in one dry run, so the user never fixes one issue only to discover the next.

## 1. What Histmersion already has, and why it is not enough

| mechanism | what it carries | why it cannot merge |
|---|---|---|
| `histmersion backup` / `restore` (`pg_dump -Fc`, `pg_restore --clean`) | everything, byte for byte | It **replaces** the whole database: `restore` refuses a non-empty database without `--force` and then wipes it. A dump is tied to one schema version and also carries derived state (`source_chunks`, `tsv`, `jobs`, `geom_clipped`). There is no way to apply half of it. |
| `histmersion export <dir>` → `seed` | per source: frontmatter (metadata, findings, `superseded_by`) and notes; per article: the note; `meta/*.md` queues | It is **lossy**. It leaves out the OCR body (`ocr_markdown`, 198 MB live), the assets (9 679 rows, 1.6 GB), typed edges other than `superseded_by`, events, attestations, aliases, local places, article versions and goals. Its only key is the citekey, and its only conflict rule is "newer `updated_at` wins unless `--force`" (plan §1.9a). Two different works can share a citekey, and one work can have two citekeys. Both cases slip through. |
| `rename` + `source_aliases` (V027) | old citekey → new citekey | The right idea (a mutable label with permanent redirects), but it covers **sources only**, within one instance. |
| `lint duplicate-doi`, `duplicate-sha256`; `ingest` returns 409 for the same bytes under another citekey | dedup **inside** one instance, on strong keys only | No fuzzy matching, and no way to say which side's record wins. |

**Verdict:** keep `backup` for disaster recovery and `export`/`seed` as the readable Obsidian mirror,
and add a third format: a **lossless, logical, identity-carrying bundle** that is built to be merged.

## 2. Prior art

### 2.1 Bibliographic deduplication

| tool / work | technique | what we take |
|---|---|---|
| **Zotero** duplicate detection ([docs](https://www.zotero.org/support/duplicate_detection)) | Matches on title, DOI or ISBN, then checks author/year. Merging picks a "master" item and lets the user choose each conflicting field. A merged item keeps every attachment, tag and collection, and the merged-away item's key still resolves for citations. | Per-field choice; union of tags and attachments; the old id keeps working. Detection works **within one library only**: group libraries are never deduplicated against each other. That is exactly our gap. |
| **Zotero sync conflicts** ([docs](https://www.zotero.org/support/sync), [forum](https://forums.zotero.org/discussion/37612/syncing-conflict-resolution)) | When the same item changes on both machines, a dialog offers local, remote or a field-by-field merged version. | The three-pane review UI. |
| **JabRef** [Find duplicates](https://docs.jabref.org/finding-sorting-and-cleaning-entries/findduplicates) / [Merge entries](https://docs.jabref.org/finding-sorting-and-cleaning-entries/mergeentries) | Edit distance weighted on author, editor, title and journal. Choices: *keep left / keep right / keep both / keep merged*, plus "remove exact duplicates automatically". Groups and keywords can be unioned. | Our decision vocabulary is JabRef's: `keep-left`, `keep-right`, `both`, `merge`. |
| **Calibre Find Duplicates** plugin ([wiki](https://github.com/kiwidude68/calibre_plugins/wiki/Find-Duplicates)) | Separate algorithms for identifiers, file bytes (binary) and title/author. Title and author each have levels: identical / similar / soundex / fuzzy, where *fuzzy* strips subtitles. | Graded normalisation levels; byte equality as its own test. |
| **OpenRefine clustering** ([in depth](https://openrefine.org/docs/technical-reference/clustering-in-depth)) | *Key collision*: the fingerprint lowercases, folds diacritics, removes punctuation and sorts tokens; n-gram fingerprints; phonetic keys. *Nearest neighbour*: Levenshtein or PPM within a radius. | The fingerprint is our cheap blocking key and our "equal after normalisation" test. |
| **Crossref reference matching** ([Tkaczyk, "Reference matching: for real this time"](https://www.crossref.org/blog/reference-matching-for-real-this-time/), [how good is your matching](https://www.crossref.org/blog/how-good-is-your-matching/)) | Search-based matching (query, then validate the candidate) scored F1 0.963 against 0.925 for the legacy parse-then-match approach. | Where a source lacks a DOI on both sides, `discover meta` (Crossref/OpenAlex) can add a DOI before matching. A key fetched from outside turns a fuzzy match into a strong one. |
| **OpenAlex** merged entities ([docs](https://help.openalex.org/api/get-single-entities/)) | When two ids name one work, one becomes canonical and the other permanently redirects (HTTP 301). | `uid_aliases`: a merged-away id keeps resolving. Histmersion already does this for citekeys. |

### 2.2 Record linkage

- **Fellegi & Sunter (1969)**, "A Theory for Record Linkage", *JASA* 64:1183–1210,
  [doi:10.1080/01621459.1969.10501049](https://doi.org/10.1080/01621459.1969.10501049). Each field
  comparison adds log-likelihood weight `log(m/u)` when it agrees and `log((1−m)/(1−u))` when it
  disagrees. Two thresholds split pairs into *match*, *non-match* and a *clerical review* zone in
  between. That middle zone is our "needs decision".
- **Winkler (1990)**, "String Comparator Metrics and Enhanced Decision Rules in the Fellegi-Sunter
  Model", *Proc. ASA Survey Research Methods* 354–359
  ([ResearchGate](https://www.researchgate.net/publication/243772975_String_Comparator_Metrics_and_Enhanced_Decision_Rules_in_the_Fellegi-Sunter_Model_of_Record_Linkage)).
  Jaro-Winkler gives extra weight to a shared prefix, which suits surnames.
- **Splink** ([GitHub](https://github.com/moj-analytical-services/splink),
  [intro](https://www.robinlinacre.com/introducing_splink/)). Fellegi–Sunter over SQL with blocking
  rules, EM-estimated m/u, and "waterfall" charts that explain each score. We copy the explanation
  idea. EM is not needed at our scale: about 340 sources per side, so hand-set m/u values do the job.
- **dedupe** ([docs](https://docs.dedupe.io/en/latest/API-documentation.html)). Active learning: it
  asks the user to label the pairs it is least sure about, and has separate `Dedupe` / `RecordLink` /
  `Gazetteer` modes. Ours is `RecordLink`: two sets, each mostly clean inside itself.
- **Papadakis et al. (2020)**, "Blocking and Filtering Techniques for Entity Resolution: A Survey",
  *ACM CSUR* 53(2), [doi:10.1145/3377455](https://doi.org/10.1145/3377455)
  ([arXiv](https://arxiv.org/abs/1905.06167)). The vocabulary for blocking keys.
- **Christen, *Data Matching*** (Springer 2012,
  [doi:10.1007/978-3-642-31164-2](https://doi.org/10.1007/978-3-642-31164-2)). Name
  standardisation (inverting "Surname, Given", dropping life dates, initials) and evaluation by
  precision and recall over labelled pairs.
- **MinHash** (Broder 1997, "On the resemblance and containment of documents",
  [doi:10.1109/SEQUEN.1997.666900](https://doi.org/10.1109/SEQUEN.1997.666900)). Estimates the
  Jaccard similarity of shingle sets, so two OCR runs of the same book can be compared cheaply.
- **Editions:** IFLA LRM / FRBR's *work → expression → manifestation* split
  ([IFLA LRM](https://www.ifla.org/publications/node/11412)). A reprint is the same work and
  expression in a different manifestation. Whether to merge one is a **policy** question, not a
  matching question.

### 2.3 Sync and merge tools

| tool | model | lesson |
|---|---|---|
| **git** three-way merge, [`git merge-file`](https://git-scm.com/docs/git-merge-file); diff3 formalised by Khanna, Kunal & Pierce ([2007](https://www.cis.upenn.edu/~bcpierce/papers/diff3-short.pdf)) | A merge needs a **common ancestor**. Without one, every difference is a conflict. | Record the revision that was last merged (the ledger), so the next merge has a base. |
| **git rerere** ([docs](https://git-scm.com/docs/git-rerere)) | Remembers how a conflict was resolved and replays that resolution when the same conflict recurs. | A resolution file keyed by **stable issue ids**, reused across runs. |
| **Automerge / CRDTs** ([docs](https://automerge.org/docs/hello/); Kleppmann et al., [local-first](https://martin.kleppmann.com/papers/local-first.pdf)) | Every replica converges deterministically. Concurrent writes to one key: one value wins (Lamport/actor order), and the losing values can still be read with `getConflicts`. | Good for sets: tags, edges, queue log entries. **Wrong for scholarship:** converging silently on one of two `status` verdicts or two article bodies is exactly what the user must see. CRDTs also need both sides to keep op logs from the start, and ours never did. |
| **Joplin** ([conflicts](https://joplinapp.org/help/apps/conflict/)) | Copies the losing note into a *Conflicts* notebook. | "Keep both and flag" is a safe fallback for articles. |
| **Obsidian Sync** ([docs](https://github.com/chrisblifeos-pixel/Obsidian-Official-Documentation/blob/master/en/Obsidian%20Sync/Troubleshoot%20Obsidian%20Sync.md); mirror of the official help, **(unverified)** against obsidian.md itself) | Merges Markdown with diff-match-patch and uses last-modified-wins for other files. Since 1.9.7 it can create a `*.sync-conflict-<ts>.md` file instead. | Automatic text merges are risky without a base. Obsidian made the conflict file opt-in after users asked for it. |
| **Anki `.apkg` import** ([manual](https://docs.ankiweb.net/importing/text-files.html), [import behaviour](https://docolin.com/imgajeed/ankipack/explanation/anki-import), **(unverified)** second-hand) | Matches notes on a **GUID** carried in the export, not on the local id. When the GUIDs match, the newer modification wins. | Portable GUIDs make re-importing the same package idempotent, and that alone removes most duplicates. |

### 2.4 Ids and hashes

- **UUIDv7** ([RFC 9562](https://www.rfc-editor.org/rfc/rfc9562.html)): time-ordered, collision-free
  across instances, with no coordination. ULID's idea, standardised. The live server runs
  **PostgreSQL 18.4**, which has `uuidv7()` built in (checked: the function exists).
- Content hashes: `sha256` already identifies originals and assets. We add a per-record `rev`, the
  sha256 of the record's canonical JSON ([RFC 8785 JCS](https://www.rfc-editor.org/rfc/rfc8785)).
- Anchoring text without offsets: the W3C Web Annotation **TextQuoteSelector** (exact + prefix +
  suffix, [spec](https://www.w3.org/TR/annotation-model/#text-quote-selector)). The `attestations`
  table already stores `quote/prefix/suffix` next to `char_start/char_end`, so an attestation can be
  re-anchored when the other side's OCR body is chosen.

## 3. Identity model

**Principle:** separate the *label* (citekey, slug: human, mutable) from the *identity* (uid:
opaque, permanent, global) from the *evidence of sameness* (DOI, ISBN, sha256: external, strong).

| layer | what | where |
|---|---|---|
| instance id | one `uuidv7` generated on first start, with a human label ("work", "home") | new `instance` table (single row) |
| entity uid | `uuidv7` per row, assigned at creation, never changed, carried by every export | new `uid uuid` column on sources, findings, articles, article_versions, assets, events, attestations, asset_links, goals, queue_items, locally added places and periods |
| origin | the instance id that created the row | `origin uuid` column, next to `uid` |
| strong keys | DOI (lowercased, `https://doi.org/` stripped), ISBN-13 (normalised from ISBN-10), OpenAlex W-id, original-file sha256 | new `source_identifiers(source_uid, scheme, value)`. **(as built)** NOT unique on `(scheme, value)`: two sources sharing a DOI or a file is exactly what `lint duplicate-doi`/`duplicate-sha256` exist to report, and the constraint would refuse the migration on a corpus that has one (whether the live one does today is **(unverified)**); indexed instead, and the matcher treats a shared key as evidence. doi and sha256 are derived from `sources` by a trigger; nothing records isbn/openalex yet |
| instance-local, never exported | `calibre_id`: Calibre is an optional, per-machine organiser of originals (see §3a). Once a source is OCR'd the database holds everything it needs, so the link is not part of the merge | stays in `sources.calibre_id` on each instance; the export omits it and the import never sets it |
| labels | citekey / slug | as now; `source_aliases` covers renames **and** merges; a new `article_aliases` covers slugs. **(as built)** `article_aliases` is not built: nothing renames an article yet, so M3 adds it with the slug rename it needs |
| redirects | foreign uid → local uid after a merge | `uid_aliases(alias_uid, uid, merge_id)`, OpenAlex-style |
| ledger | for each imported foreign uid: the local uid it maps to, the foreign `rev` merged, the resulting local `rev`, and the decision | `merge_ledger` |

What this makes cheap:

1. **Second and later merges.** Every foreign uid seen before is found in `uid_aliases` or the
   ledger, so it needs no matching. If its `rev` equals the ledger's `foreign_rev`, it has not changed
   and is skipped. That is the idempotency guarantee: importing the same bundle twice changes nothing.
2. **Three-way merges.** The ledger's `foreign_rev` and `local_rev_after` give the common ancestor of
   a record that changed on both sides after the last merge. Article bodies can be found by hash in
   `article_versions`.
3. **Forks.** An instance first created *from* a bundle (home seeded from work) shares uids with it,
   so the later merge back recognises every shared row at once, just as Anki matches on GUIDs.

Back-filling the existing ~340 sources: give each a fresh uid with `origin` = the local instance. For
two instances that already exist, the first merge has no shared uids and relies entirely on matching.
That first merge is the expensive one. Every merge after it is cheap.


### 3a. Calibre is outside the export

Decision (user, 2026-10-03): Calibre is optional. A user may organise originals in it, but once a source
is OCR'd the Histmersion database holds everything the corpus needs: metadata, body, assets, findings,
edges, and the server's own stored copy of the original (by sha256). Export and import therefore move
**database content only**. `calibre_id` is a per-machine pointer and is neither exported nor matched on;
Calibre identifiers never serve as a matching key. A merged instance can rebuild a Calibre library from
the stored originals with `calibre sync --apply` if its user wants one.

## 4. Export format: the `.hmx` bundle

A **zstd-compressed tar** (or a plain directory when given `--dir`), deterministic so that two exports
of an unchanged database are byte-identical. **(as built)** gzip, not zstd (zstd needs a JNI library
in two native images; gzip is in the JDK), a hand-written ustar with fixed headers (mode 0644, owner
0/0, mtime 0) so the bytes are deterministic, and no `--dir` form yet. The file layout as built is in
§7.5.

```
manifest.json
entities/sources.ndjson          # one JSON object per line, sorted by uid
entities/findings.ndjson
entities/edges.ndjson            # {src_uid, dst_uid, kind}
entities/unresolved_edges.ndjson
entities/articles.ndjson         # + versions inline as [{uid, body_sha256, summary, updated_at}]
entities/assets.ndjson           # metadata only; bytes in blobs/
entities/asset_links.ndjson  entities/attestations.ndjson
entities/events.ndjson           # datings / participants / edges nested
entities/places.ndjson           # ONLY places not identical to the bundled seeds (by id + rev)
entities/periods.ndjson          # likewise
entities/goals.ndjson  entities/queue.ndjson  entities/aliases.ndjson
blobs/sha256/ab/abcdef….         # OCR bodies, article version bodies, asset bytes, originals (opt.)
```

`manifest.json`:

```json
{ "format": "histmersion-bundle", "formatVersion": 1, "schemaVersion": "V031",
  "bundleId": "0192f6c4-…", "instance": {"id": "0191a2…", "label": "work"},
  "exportedAt": "2026-10-03T09:12:00Z", "since": null,
  "counts": {"sources": 338, "findings": 1950, "edges": 589, "articles": 72, "assets": 9679},
  "blobs": {"count": 10112, "bytes": 1874000000},
  "seeds": {"places": "pleiades@2026-08 cliopatria@… ne110m", "periods": "periodo-seed@…"},
  "sha256": {"entities/sources.ndjson": "…", "…": "…"} }
```

Every entity line carries `uid`, `origin`, `rev`, `updatedAt` and its **label** (citekey/slug). It
references other entities **by uid** (`citekey` fields are kept for readability only). **(as built)**
`rev` covers everything but `rev` and `updatedAt`, so equal content has an equal rev whatever its
timestamps; and the manifest has no wall-clock `exportedAt` (it would break byte-equality): `asOf`,
the newest change the bundle carries, is the value for the next `--since`, and `bundleId` is derived
from the instance and the content. Left out:
derived or re-derivable state (`source_chunks`, `tsv`, `finding_places`, `geom_clipped`, `land`,
`jobs`, `retraction_watch`) and machine-local `agent_state`. `assessments` are included, and on
import the newer `checked_at` wins.

**Why NDJSON plus blobs, and not markdown or `pg_dump`.** It is lossless, unlike `export`. It is
independent of the schema version and partially applicable, unlike `pg_dump`: the format is versioned
separately and the importer upgrades older format versions. It streams, so 1.6 GB of assets never sit
in memory. It diffs line by line in git. Content-addressed blobs deduplicate themselves (the live
assets are 9 679 rows over 9 614 distinct hashes). **Incremental:** `--since <merge-id|timestamp>`
exports only rows with a newer `updated_at`, plus tombstones (`{"uid":…, "deleted":true}`) from a new
`tombstones` table. Without tombstones, a deletion at work would quietly come back at home on the next
merge.

**Cons:** a third format to maintain, and it must track every migration. Mitigate this with one
round-trip property test per table (§8). Bundles carrying full assets are large (~2 GB). `--no-blobs`
plus a `have.txt` of known hashes, exported from the receiving side, lets the sender ship only what is
missing.

## 5. Matching pipeline

### 5.1 Order of operations

Entities are matched in dependency order. Each stage writes into an **id map** (`foreign uid → local
uid | NEW`), and the stages after it read from that map.

1. **Reference data:** places (bundled-seed ids are equal on both sides, so only local additions are
   matched), periods.
2. **Sources:** the hard part, §5.2.
3. **Things owned by a source:** findings, assets, attestations, asset_links. Each is matched **only
   inside a matched source pair**, so blocking is free.
4. **Graph:** edges and unresolved edges, re-pointed through the map.
5. **Prose and bookkeeping:** articles (with `[[citekey]]` rewritten through the map), events, goals,
   queues, aliases.

### 5.2 Sources

**Stage A: identity, no scoring.** Look the foreign uid up in `uid_aliases` and the ledger. If found,
the pair is matched and the only question left is whether the record changed since the last merge
(`rev`).

**Stage B: strong keys.** These either decide or block.

| evidence | outcome |
|---|---|
| same DOI | match |
| same original sha256 | match. If both sides have a DOI and the DOIs differ: **blocking** `S-DOI-CONFLICT`, because one record has the wrong DOI |
| same ISBN-13 / OpenAlex id | match |
| DOI on one side only | **neutral**, weight 0. Missing is not disagreement, and the DOI is carried over on merge |
| foreign citekey equals a local **alias** (or the reverse) | strong signal, +8. The live corpus has 49 aliases, such as `latacz2023_troy → latacz2004_troy` and `page2024_history → page1959_history` |
| DOIs differ, one a book and the other a chapter (Crossref `type`) | not a match on its own; becomes a `part_of` hint |

**Stage C: blocking**, for pairs with no strong key. Candidates are pairs that share any one of:

- the *title fingerprint prefix*: OpenRefine fingerprint (case-folded, diacritics folded, punctuation
  removed) of the main title (cut at the first `:`, ` - `, `. `), first 3 significant tokens sorted;
- the *first-author surname key*: names parsed from either `Surname, Given, 1850-1928` (live:
  `Harrison, Jane Ellen, 1850-1928`) or `Given Surname` (live: `Francis Young`), life dates dropped,
  then ASCII-folded (`Güterbock` → `guterbock`), plus a Double-Metaphone key;
- the citekey stem (`guterbock1997_`) with the year ignored.

At ~340 × ~340 the full cross product (115 k pairs) is also affordable, so blocking is there for
predictable runtime, not out of necessity.

**Stage D: score.** Fellegi–Sunter-style log-weights with hand-set m/u, all in a config block
(`merge.weights.*`) so they can be tuned without a rebuild:

| field | comparison levels (weight) |
|---|---|
| title | fingerprint equal (+9) · main title equal, subtitle differs (+7) · JW ≥ 0.92 (+5) · token-set Jaccard ≥ 0.7 (+2) · else (−6) |
| authors | surname multiset equal, any order (+6) · first surname equal + initials compatible (`H. G.` ~ `Hans G.`) (+4) · overlap ≥ 50 % (+2) · disjoint (−5) |
| year | equal (+3) · ±1 (+1.5, online-first vs print) · differs, and one side's citekey/alias/notes say "reprint/ed./transl." (+0.5) · otherwise ±>1 (−2) |
| venue | fingerprint equal (+1) · one missing (0) · differ (−0.5) |
| body | both OCR'd and MinHash Jaccard of 5-word shingles ≥ 0.8 (+8) · 0.3–0.8 (+2, could be a different edition) · < 0.1 (−4) |

**Classification.** `score ≥ 14` with no blocking flag → **auto-merge**. `score ≤ 4` → **new**: kept
as a separate source, nothing to decide. In between, or any hard flag → **needs decision**. Hard
flags that **always** block, whatever the score:

- different DOIs on both sides;
- years more than 1 apart: the edition question (§2.2). The default suggestion is to merge under the
  **earliest** year, which matches the `page2024 → page1959` rename practice. A policy flag
  `--editions merge|separate` turns this into an automatic decision;
- citekey collision: **same citekey, different work** (score ≤ 4). The foreign source gets a suggested
  new key (`smith2019_ritual` → `smith2019_ritualb`, or the next title word), and every foreign
  `[[link]]` follows.

### 5.3 Merging two matched sources field by field

| field | rule |
|---|---|
| title, authors, venue | equal after normalisation → keep the **richer** form (subtitle present, diacritics kept, full given names). Really different → **blocking** `S-META` |
| year | equal → keep. ±1 with a DOI match → keep the version-of-record year (Crossref `published-print`), auto. Otherwise → blocking |
| doi, isbn, openalex | union (a conflict was already caught in stage B) |
| topics, regions, tags | **set union** after tag canonicalisation (the `non-canonical-tag` lint rule's normaliser) |
| period | one null → the other. Different → blocking, suggesting the hull `[min,max]` |
| status | equal → keep. One side `unassessed` → the other. Otherwise → **blocking**: a scholarly verdict is never chosen by timestamp |
| summary, notes | equal / one empty → trivial. Base known from the ledger → three-way text merge, auto when clean. Else → blocking (`keep-left`, `keep-right`, `concat`, `file:<path>`) |
| ocr_markdown + assets | sha256 equal → keep local. Differ → **non-blocking default** `keep-left`: the local body stays, and the foreign body's attestations are re-anchored by quote/prefix/suffix (an attestation that cannot be re-anchored becomes a blocking issue). Option `keep-right` swaps body and figures and re-runs chunking |
| calibre link | not merged: the local `calibre_id` (if any) is kept, the foreign side has none in the bundle |
| cited_by_count, assessments | newer `checked_at` wins |
| citekey | local key wins. The foreign key becomes an **alias**, so `[[foreign_key]]` in foreign articles keeps resolving even before rewriting |

### 5.4 Dependent entities

| entity | key / blocking | auto | needs decision |
|---|---|---|---|
| **findings** | within a merged source. Normalised claim (fingerprint + stopwords removed), period overlap, distance ≤ 50 km or same `region_name` fingerprint | fingerprints equal → one finding, tags unioned, the more specific period/point kept | Claim token-Jaccard ≥ 0.6 with overlapping period: **non-blocking default `both`**, listed so the user can say `merge`. Equal claim but different `confidence` → keep the lower and report it (cautious) |
| **finding ids** | `<citekey>#<n>` is a label | foreign findings renumbered after the local maximum. `finding_places` rebuilt | — |
| **edges** | `(src_uid, dst_uid, kind)` mapped | set union. A self-loop created by merging A and A′ (for example `A superseded_by A′`) is dropped and reported | `A refutes B` on one side and `A references B` on the other: both kept (edges form a set), reported under *info* |
| **unresolved_edges** | dst re-resolved against the merged corpus (aliases, DOI) | promoted to `edges` when the target now exists | — |
| **articles** | slug; uid when shared | Equal body → nothing. One side's body is a version the other already has → fast-forward. Common base in `article_versions` → diff3, auto when clean. Foreign `[[ck]]` links rewritten through the id map, each rewrite stored as a version (as `rename` does) | **Blocking** when diff3 conflicts or there is no common base: `keep-left`, `keep-right`, `both` (foreign becomes `<slug>-<instance-label>`), `file:<path>`. A merged article is **not reviewed**: it is queued for `/review-article` |
| **article_versions** | uid | the union, interleaved by time, with the origin shown | — |
| **assets** | `(source, sha256)` | same sha → one row. Name clash with a different sha → foreign renamed `name~<instance>`, links in the kept body rewritten | — |
| **attestations** | `(source, target, quote hash)` | re-anchored by TextQuoteSelector when bodies differ | anchor not found → blocking (`drop`, `keep-unanchored`) |
| **events** | id slug; else label fingerprint + overlapping `outer_span` | same id and same rev → nothing. Datings/participants unioned per `(event, citekey)` | same id, different spans/geometry → blocking |
| **places (local additions)** | id; else `place_links` external ids (Pleiades/Wikidata URIs); else label fingerprint + geometry IoU ≥ 0.8 | same id + rev → nothing | same id, different geometry → blocking (UI shows both polygons on a map) |
| **goals** | slug | status: take the further-along state on the `planned→…→done` ladder; notes concatenated with origin headers | — |
| **queues** | `log`: none (append, sort by date). `acquire`/`tasks`: normalised text | `done` anywhere → `done`. Exact text duplicates dropped | — |
| **aliases** | alias string | union | foreign alias names a **different** local source → blocking |

## 6. Resolution UX

### 6.1 Commands

| CLI | REST | MCP |
|---|---|---|
| `histmersion bundle export <file> [--since <merge-id>] [--no-blobs] [--have have.txt]` | `GET /api/bundle?since=` (stream) | `bundle_export` |
| `histmersion bundle verify <file>` (checksums, format and schema version) | `POST /api/bundle/verify` | — |
| `histmersion merge plan <file> [--resolutions r.yaml] [--resolve ID=decision]... [--policy p.yaml] [--write-template r.yaml] [--json]` | `POST /api/merge/plan` → `MergePlan` | `merge_plan` |
| `histmersion merge apply <file> --resolutions r.yaml [--yes]` | `POST /api/merge/apply` → job | — (a human's call, like `restore`) |
| `histmersion merge list` · `merge show <merge-id>` · `merge ledger <uid\|citekey>` | `GET /api/merges…` | `merges` |

`plan` is **pure**. It writes nothing, it is deterministic, and it always reports **every** issue
(the requirement): the stages never stop early. A stage whose input depends on an unresolved upstream
decision is planned under **both** alternatives and its issues are listed as *conditional* (`if
S-3c1e=both`), so the user never finds a new issue only after resolving an earlier one.

**Issue ids** are `<class>-<first 6 hex of sha256(class, sorted foreign uids, sorted local uids,
foreign rev, local rev)>`. They stay stable across runs while the records do not change. If either
side changes, the id changes, and the old resolution is reported as **stale**, never silently
re-applied (as rerere does with a changed hunk).

**`apply`** refuses unless (1) every blocking issue has a decision, (2) re-planning with the given
resolutions gives the same `planHash`, so the database has not moved since the plan, and (3) a fresh
`pg_dump` was taken (an automatic `histmersion backup`, whose name is stored in `merges`). It then runs
in **one transaction**, writes `merges`, `merge_ledger` and `uid_aliases`, adds a `log` queue entry,
and queues `/review-article` tasks for every article it changed.

### 6.2 Example: the dry run

(Illustrative: the citekeys are real shapes, the conflicting values are invented.)

```
$ histmersion merge plan ~/Downloads/work-2026-10-03.hmx
Bundle   work (0191a2…) → home (0193c0…) · format 1 · schema V031 · exported 2026-10-03 09:12
Matched  412 sources: 301 by uid/ledger · 58 by DOI/sha256 · 21 by score · 32 new
Summary  9 blocking · 14 defaults (applied unless overridden) · 37 info · plan 7c41e9a2

BLOCKING
 S-3c1e7a  source  same work, two citekeys (identical file)
     work  guterbock1997_perspectives   "Perspectives on Hittite Civilization: Selected Writings"
           H. G. Guterbock · 1997 · doi 10.… · sha256 9f2e…
     home  guterbock1997_hittite         "Perspectives on Hittite civilization"
           Güterbock, Hans G. · 1997 · no doi · sha256 9f2e…   ← identical file
     why   sha256 equal (strong key) · main title equal (+7) · surnames equal (+6)
     only  title differs (subtitle) → auto · citekey differs → choose
     decide  map:guterbock1997_perspectives | map:guterbock1997_hittite
 S-91d0b4  source  DOI conflict on identical file
     work  kuhrt1995_near  doi 10.4324/9780203431566   home  kuhrt1995_ancient  doi 10.4324/9781315682334
     decide  keep-left | keep-right
 S-0aa4f3  source  status differs: work current · home contested (challenges ← heggarty2023_language)
     decide  keep-left | keep-right
 S-5be210  source  citekey collision, different works (score −11)
     both  smith2019_ritual   work: "Ritual and Kingship…" (M. Smith)   home: "Ritual Economies…" (J. Smith)
     decide  rename-right:smith2019_ritualkingship (suggested) | rename-left:<ck>
 A-e82c19  article bronze_age_collapse  diverged from common base v214 (2026-09-12): 3 conflict hunks
     decide  keep-left | keep-right | both | file:<path>
 A-17b0d2  article hittite_storm_god   no common base (created on both sides)
 P-44c9e0  place   hittite_empire_-1400  geometry differs (IoU 0.62)
 T-2f19aa  attest  #12 on kuhrt1995_ancient: quote not found in kept body
     decide  drop | keep-unanchored | keep-right-body (resolves via S-91d0b4)
 L-6d3301  alias   latacz2023_troy → work: latacz2004_troy · home: latacz2023_troy is a different source

DEFAULTS (override with --resolve)
 F-a0c7e1  finding  near-duplicate (Jaccard 0.71) on maeir2025_deathscapes #3 ↔ #5   default: both
 O-1c55d0  ocr      bodies differ on olsen2015_penelope (MinHash 0.94)            default: keep-left
 … 12 more (--all)

INFO  37 (edges unioned 211 · self-loop dropped 1 · tags unioned 64 · queue dupes dropped 9) — --info

Next: histmersion merge plan … --write-template resolutions.yaml
```

### 6.3 Resolution file, and partial re-plans

```yaml
# resolutions.yaml: generated by --write-template, plan 7c41e9a2
bundle: 0192f6c4-…
policy:
  editions: separate          # turns every year-gap issue into keep-both
  findings-near-duplicate: both
resolve:
  S-3c1e7a: map:guterbock1997_perspectives   # home key becomes an alias
  S-91d0b4: keep-left
  S-0aa4f3: keep-right        # contested stays; the challenges edge comes along
  S-5be210: rename-right:smith2019_ritualkingship
  A-e82c19: file:merged/bronze_age_collapse.md
  # A-17b0d2:                 # still open
```

```
$ histmersion merge plan work.hmx --resolutions resolutions.yaml
Summary  1 blocking (A-17b0d2) · 0 stale · 14 defaults · plan 7c41e9a2+r(5)
         T-2f19aa now unconditional: S-91d0b4=keep-left keeps home's body; quote re-anchored (chunk 41)
```

Flags override the file: `--resolve A-17b0d2=both`. Unknown or stale ids are errors, never ignored.

### 6.4 After apply

```
Merged work.hmx as merge 0193f1… (backup histmersion-20261003-0931.dump, 1.9 GB)
sources  +32 new · 380 merged (21 by score) · 2 renamed · aliases +23
findings +611 · merged 88 · edges +211 · articles +6 new · 3 three-way · 1 both (hittite_storm_god-work)
queued   4 /review-article tasks · log entry #590
re-run   histmersion merge plan work.hmx → 0 changes (idempotent)
```

### 6.5 Web UI

A `/merge` screen with the same plan JSON. On the left, an issue list grouped by class, with blocking
first, the counter "9 → 0", and a filter. On the right, a review pane chosen by entity type:

- **source**: a Zotero/JabRef-style three-column field table (work | result | home) with a radio
  button per field, the score "waterfall" (Splink) explaining why the pair matched, and links to both
  `/source/` pages;
- **article**: a diff3 view (base | work | home), with conflict hunks editable in place;
- **place / event**: both geometries on the existing Leaflet map, and spans on the timeline.

Decisions go into a server-side `merge_sessions` draft that can be exported as `resolutions.yaml`, so
CLI and UI share one format. *Apply* stays a button that needs confirmation and shows the backup step.

## 7. Concrete proposal for Histmersion

### 7.1 Migrations

**(as built)** numbered V030–V033 (V029 was taken): V030 instance + uids + change clocks, V031
tombstones, V032 `source_identifiers`, V033 `merges` + `merge_ledger`; M3 added V036 `uid_aliases`;
M4 V042 (renames: ON UPDATE CASCADE into `articles`/`events`/`goals`, and `article_aliases`); M5
V043 (`merges.plan_hash`/`backup`/`resolutions`, `merge_decisions`); M6–M7 V051 `merge_sessions`, V052
`places.dataset`/`periods.dataset` (+ change clocks and tombstones), V053 `event_aliases`/`goal_aliases`,
V054 `goals.plan_approved_at` (+ goal links and schedules move the goal's clock).

- **V029__instance_and_uids.sql**: `instance(id uuid pk default uuidv7(), label text, created_at)`;
  `uid uuid NOT NULL DEFAULT uuidv7() UNIQUE` and `origin uuid` on the tables listed in §3, back-filled
  to the local instance; `tombstones(uid, kind, deleted_at)` maintained by the delete paths.
- **V030__identifiers.sql**: `source_identifiers` (back-filled from `doi`, `sha256`), `article_aliases`.
- **V031__merge_ledger.sql**: `merges(id, bundle_id, bundle_sha256, from_instance, plan_hash,
  resolutions jsonb, report jsonb, backup text, applied_at)`, `merge_ledger(merge_id, kind,
  foreign_uid, foreign_rev, local_uid, local_rev_after, decision)`, `uid_aliases`, `merge_sessions`.

`sources.citekey` stays the primary key, so nothing that joins on it changes. `uid` is an extra
unique key. Over time the export and merge code works only with uids.

### 7.2 Code placement

`core`: the bundle DTOs, the `MergePlan`/`Issue`/`Decision` ADT, and the **pure** matcher and
normalisers (names, titles, fingerprints, Jaro-Winkler, MinHash), which are easy to unit- and
property-test. `db`: `BundleWriter` (streams NDJSON), `BundleReader`, and `MergeApply` (one
transaction). `server`: endpoints and the apply job. `cli`: `bundle`/`merge`. Every surface renders
the same Markdown, as usual.

### 7.3 Milestones

| # | deliverable | acceptance | effort |
|---|---|---|---|
| M0 | identity: V029–V030, uids in DTOs and `get_source`, tombstones | every row has a uid; `rename` keeps it | 2–3 d — **built** |
| M1 | `bundle export/verify`, deterministic, blobs, `--since` | two exports of an unchanged database are byte-equal | 3 d — **built** |
| M2 | import with **no** matching: into an empty instance, and back into its own origin | export → import into an empty instance → export is byte-equal; importing your own bundle changes 0 rows | 2–3 d — **built** |
| M3 | source matcher + `merge plan` + resolution file + stable issue ids | crafted fixture (§8) gives exactly the expected issue list in one run | 5–7 d — **built** |
| M4 | dependent entities: findings, edges, assets, attestation re-anchoring, articles diff3, events, places, queues, aliases | every row of the §5.4 table has a fixture | 6–8 d — **built** (places not: §7.7) |
| M5 | `merge apply`: backup, plan-hash guard, ledger, report, review tasks | apply, then re-plan → 0 changes | 3 d — **built** |
| M6 | web `/merge` review screen | resolve the fixture's issues in the UI alone | 4–6 d — **built** |
| M7 | MCP `merge_plan`/`merges`, skill text (`histmersion-ingest`), README rows | — | 1 d — **built** |

Total about 6 weeks of focused work. M0–M2 are useful on their own: home can be **forked** from work
cheaply, after which every later merge is mostly matching by uid.

### 7.4 Risks

| risk | mitigation |
|---|---|
| **A false merge destroys a distinct work.** This is the worst failure. | No auto-merge without a strong key **or** score ≥ 14 with no hard flag. Automatic backup before apply. `uid_aliases` plus the ledger make an `unmerge` feasible later (out of scope). Report precision/recall on the labelled set (§8) before trusting the thresholds. |
| An agent "resolving" a scholarly conflict on the user's behalf | `merge apply` is not an MCP tool. Status, article and edition decisions always block. Skills are told to present the plan, not decide it. |
| The bundle format drifts from the schema | one round-trip property test per table, which fails when a migration adds a column the bundle does not carry |
| Stale plans | the plan hash covers both sides' `rev`s. Apply re-plans and compares |
| Large bundles (assets 1.6 GB) | blobs by hash, `--have`, `--no-blobs`, streaming |
| Attestation offsets after a body swap | TextQuoteSelector re-anchoring. A failure blocks and is never silent |
| Calibre | out of scope: the bundle carries only database content (including the server's stored originals, by sha256). The merge never reads or writes Calibre; a user who keeps Calibre runs `calibre gaps` / `calibre sync` afterwards on their own machine |
| Seed drift: work and home seeded different Pleiades snapshots | the manifest records seed versions. A mismatch is *info*, and seed places are compared by `rev` only when the versions match |

### 7.5 As built: M0–M2

**Identity (M0).** `instance` (one row, made by V030) and `uid uuid` (UUIDv7) + `origin uuid` on
sources, findings, articles, article_versions, assets, asset_links, attestations, events, goals,
queue_items, places and periods — back-filled with an UPDATE rather than a volatile column default,
so the TOASTed `assets.bytes` and `sources.ocr_markdown` are not rewritten. The tables with no change
clock (findings, assets, asset_links, attestations, events, queue_items) gained `updated_at`, moved
by a BEFORE UPDATE trigger unless the statement sets it. Owned sets move their owner: an insert or
delete of an edge (or parked edge) moves its source's `updated_at`, an event's datings, participants
and outgoing edges move the event — so `--since` carries the owner, and with it the whole set.
Every delete of a uid row — cascades included — leaves `tombstones(uid, kind, origin, deleted_at)`
(V031, by trigger: most of these deletes are `ON DELETE CASCADE` that no repository sees). Every
trigger stands aside while `histmersion.importing` is on, so an import writes the bundle's own
timestamps and tombstones. `get_source`, its findings and `GET /api/articles/{slug}` return `uid`
(the source also `origin`); `rename` keeps the uid.

**The bundle (M1).** `histmersion bundle export <file> [--since <moment>]` → `GET /api/bundle`. Read in
one `REPEATABLE READ` snapshot; entity files built in memory (they are small), blobs streamed one at
a time from Postgres or the stored-originals spool. Files, always all present (empty ones too):

```
manifest.json                       format, formatVersion, schemaVersion, bundleId, instance{id,label},
                                    since, asOf, files[{path, lines, bytes, sha256}], blobs{count, bytes},
                                    reference{places, placesDigest, periods, periodsDigest}
entities/sources.ndjson             + bodySha256, originalSha256, originalExt (never calibre_id)
entities/findings.ndjson            sourceUid, id (the label), lon/lat as doubles
entities/articles.ndjson            body inline
entities/article-versions.ndjson    articleUid, bodySha256 — its own file, not inline in the article
entities/assets.ndjson              sourceUid, sha256, geometry as hex EWKB (exact; WKT is not)
entities/asset-links.ndjson  entities/attestations.ndjson   target label + targetUid when it has one
entities/events.ndjson              datings, participants and OUTGOING edges nested; placeId by id
entities/goals.ndjson  entities/queue.ndjson                parent by uid; a queue row's serial id is not carried
entities/edges.ndjson               {srcUid, dstUid, kind}
entities/unresolved-edges.ndjson    {srcUid, dst, kind}
entities/assessments.ndjson  entities/aliases.ndjson  entities/tombstones.ndjson
blobs/sha256/ab/abcdef…
```

**Places and periods are not carried.** They are reference data every instance seeds from the same
bundled files (`histmersion init`), and the schema cannot tell a seed row from a local addition: the
CLI's `places seed` and a user's own LPF file both arrive through `POST /api/places/import`. The
manifest fingerprints both tables, an import notes a difference, and an event whose `placeId` the
receiving gazetteer lacks refuses the import. Carrying local additions needs a `dataset` marker on
`places`/`periods` set by the import paths — an M4 item, with place matching.

`histmersion bundle verify <file>` → `POST /api/bundle/verify` (the file is the body): format and schema
against the server, every file checksum and line count, every record's rev, every blob's sha256,
and — for a full bundle — every reference; every problem in one list.

**Import without matching (M2).** `histmersion bundle import <file> [--dry-run]` →
`POST /api/bundle/import`. One transaction, two passes over the file (rows, then blobs, each blob
hashed as it is consumed). A line is the local row with the same uid or nothing; `BundlePlan`
decides it: same rev → unchanged; own bundle → keep ours; ledger says theirs is unchanged → keep
ours; ledger says ours is unchanged → fast-forward to theirs; otherwise a conflict, reported and left
alone; absent and tombstoned here → not brought back (and whatever requires it is skipped with it);
absent → insert. A foreign tombstone deletes a row only when it is still what the last import left.
Before anything is written, every blocking problem is collected: a label (citekey, alias, finding
id, slug, event id, goal slug, asset name) that a different local row has, a renamed article/event/
goal (no rename path yet), a required reference to nothing, a missing blob, a missing gazetteer
place, a newer format or schema. A source renamed on the other side is renamed here first through
the ordinary `rename` (findings, links and the alias follow). The ledger keeps one row per foreign
uid; a KEPT row leaves the old ancestor in place (ours has changes theirs lacks), so a later change
over there is the conflict it is. An import that changes nothing writes nothing — not even a
`merges` row. Derived state is rebuilt for what changed (FTS chunks, `finding_places`,
`article_links`, parked edges the new sources resolve).

Not built, from §4/§6.1: `--no-blobs`/`--have`, `--dir`, `--since <merge-id>` (a moment only), the
MCP `bundle_export` (a binary download, like a backup). `bundle instance [--label]` (`GET/PUT
/api/instance`) was added to name a collection.

**What M3 needs next.** (1) `uid_aliases(alias_uid, uid, merge_id)` and an id map `foreign uid →
local uid | NEW`, consulted where the importer now assumes `local uid = foreign uid` (the ledger's
`local_uid` column is already there); (2) the source matcher of §5.2 over `source_identifiers` and
the normalisers; (3) `merge plan` as a pure function of (bundle entities, local entities, ledger,
resolutions) — `BundleImport`'s pass 1 already gathers exactly that local state and collects every
problem, so it becomes the plan's input stage, and its label-clash problems become the
`S-…`/`A-…` issues; (4) `article_aliases` with a slug rename, and a rename path for events and goals,
so a fast-forward can follow those too; (5) the `dataset` marker for local places/periods (M4).
Open questions found while building: a re-conversion (`reocr`) replaces a source's assets with NEW
uids (and its asset links go with them by cascade) — fine for the bundle, but the matcher will see
"deleted + new" rather than "changed"; `AssetRepo.replace` could upsert by name to keep them. An
alias deleted by renaming back leaves no tombstone. Two servers migrating fresh databases in one
cluster at the same moment collide on V011's role creation (pre-existing; seen while testing).
(Resolved since: `AssetRepo.replace` keeps a row whose name or sha256 survives, links and all
(#826); renaming back records the dropped alias in V050 `alias_tombstones`, and the planner skips a
bundle alias made before it (#827); `Migrate.run` creates the role race-free and retries a
migration that collides with a concurrent one (#828).)

### 7.6 As built: M3

**Identity first.** V036 `uid_aliases(alias_uid → uid, kind, merge_id)`: a merged-away uid keeps
resolving (OpenAlex-style). `MergePlanner.mapUid` follows a redirect to its end, else the ledger's
`local_uid`, else the uid itself; the bundle's lines are then rewritten into this collection's uids
and labels (`MergePlanner.localize`: every mapped uid, own or referenced, replaced; a mapped row's
citekey/slug/id replaced by the LOCAL label — after a merge the local label wins) before anything is
compared, so the M2 machinery works unchanged on mapped rows. The ledger keeps the BUNDLE's rev as
`foreign_rev` and the rev of the line as written here as `local_rev_after`; for an unmapped row the
two are the same, as in M2. Nothing writes `uid_aliases` yet (that is `merge apply`, M5); the id map
is exercised by a test that writes what M5 will (`db.MergeSuite`).

**The matcher** (`core.SourceMatcher`, `core.MergeText`, pure): the bundle's sources that no uid,
redirect or ledger row names are matched against this collection's unclaimed ones. Strong keys from
`source_identifiers` decide: the same DOI (normalised as V032 does), original file (sha256) or ISBN
(the bundle line gained an optional `isbns`, absent when empty so older lines keep their rev). A DOI
on one side only is neutral; two different DOIs are a hard flag (or, beside a strong key, a
`doi-differs` issue). Blocking on the title key (first three significant words of the main title,
sorted), the first author's surname keys (whole and last word: `López-Ruiz` and `Lopez Ruiz` share
`lopezruiz` and `ruiz`), the citekey stem and every label (citekey or alias). Names are parsed from
`Surname, Given, 1850-1928` and `Given Surname` (life dates, Jr./Sr. dropped, diacritics folded); a
guessed surname is compared as every trailing run of its words, given names by initials (`H. G.` ~
`Hans G.`); author lists match one-to-one in any order. The score is §5.2's table, its weights and
the two thresholds in `merge.*` (`reference.conf`), a citekey of one side that is an old citekey of
the other adds `merge.weights.alias`, and the OCR bodies of a blocked pair whose bodies differ are
compared by MinHash (64 seeded MurmurHash3 functions over word 5-shingles; the local body from the
database, the bundle's from one pass over its blobs, only for those pairs). The Double-Metaphone key
was not built: ASCII folding plus the trailing-run comparison covered every name form of the
fixture. Assignment is one-to-one and greedy, strongest first.

**The plan** (`core.MergePlanner.plan`, pure; `db.MergeState` gathers its input in one read-only
`REPEATABLE READ` snapshot). Issue kinds and their decisions (left = here, right = the bundle):

| kind (id letter) | when | decisions | an import can carry out |
|---|---|---|---|
| `same-work` (S) | a pair scored between the thresholds, or above with a hard flag | `map:<either citekey>` · `both` | `both` (the bundle's is inserted) |
| `citekey-collision` (S) | a NEW source whose citekey is a citekey or old citekey here | `rename-right:<ck>` (suggested: the key plus the next title word) · `rename-left:<ck>` | — |
| `doi-differs` `year-differs` `status-differs` `title-differs` `authors-differs` (S) | a pair to be merged disagrees; year: not ±1 with the same DOI | `keep-left` · `keep-right` (year: earliest suggested) | (with the pair) |
| `summary-differs` `notes-differs` (S) | both non-empty and different | `keep-left` · `keep-right` · `concat` | (with the pair) |
| `period-differs` (S) | both set and different | `keep-left` · `keep-right` · `hull` (suggested) | (with the pair) |
| `body-differs` (O) | both OCR'd, bodies differ — a default | `keep-left` (default) · `keep-right` | (with the pair) |
| `changed-both-sides` (S/F/A/…) | M2's conflict | `keep-left` · `keep-right` (articles also `both` · `file:`) | `keep-left`, `keep-right` |
| `deleted-there-changed-here` (S/G/…) | a tombstone over a row changed here | `keep-left` · `keep-right` | both |
| `article-diverged` (A) | one slug, two articles (a default `keep-left` when the text is identical) | `keep-left` · `keep-right` · `both` · `file:` | `keep-left` |
| `label-taken` (E/G/F/X/S) | an event id, goal slug, finding id, asset name or citekey a different record has here | `keep-left` · `keep-right` | `keep-left` |
| `alias-conflict` (L) | an old citekey of the bundle names a different source here | `keep-left` (· `keep-right` when it is an alias here) | `keep-left` |

A pair's field issues are unconditional for an auto-merge and CONDITIONAL (`if S-… = map`) under an
open `same-work` issue; the collision a `both` would cause is conditional the other way, and an alias
conflict appears under whichever alternatives it exists in. Resolving the gate turns its issues
unconditional or drops them; a resolution for a dropped one is reported. Ids are
`<letter>-<8 hex>` of sha256(kind, bundle uids, local uids, the two revs, and the alias for an alias
conflict) — eight hex rather than six: at ~1 000 issues six would collide in a few per cent of plans.
`planHash` covers the bundle id, every match, every issue id with its decision, and the problems.
`--editions merge` drops the year flag (the year issue then takes the earliest year, decided by
policy); `separate` makes such a pair two works. Not built from §5.3: the "richer form" choice for
title/authors/venue (M5 applies it), a venue issue (variants like `JNES` / `Journal of Near Eastern
Studies` would block every pair), the three-way text merge of summary/notes from a ledger base (M4).

**Surfaces.** A resolution set is JSON and an upload is one body, and tapir's netty server has no
multipart: so `POST /api/bundle/upload` keeps the file under `bundles.dir/upload-<sha256>.hmx` (for
`bundles.stale-minutes` after its last use) and `POST /api/merge/plan` / `POST /api/merge/import` name
it by sha256 — which is also what lets the MCP tool `merge_plan` (read-only) plan a bundle with no
file transfer. CLI: `histmersion merge plan <file> [--resolutions f.yaml] [--resolve ID=decision]…
[--editions merge|separate] [--write-template f.yaml] [--all] [--json]` (uploads, plans, prints §6.2's
shape as Markdown) and `histmersion bundle import <file> [--dry-run] [--resolutions …] [--resolve …]
[--editions …]`. The resolution file is §6.3's (`bundle`, `policy.editions`, `resolve: {id: decision}`;
an id with no value is open; unknown keys are errors).

**The import** now takes its decisions from the plan (`BundleImport` → `MergeState.plan`): M2's
label-clash problems became issues, its reference/place/format checks the plan's problems. It
refuses — listing every open issue, unusable resolution and decision only `merge apply` can carry out
(any auto-merge or `map:`, `rename-*`, `keep-right` on a taken label, `both`/`file:` on an article) —
and otherwise applies M2's writes plus `keep-left` on a conflict (new decision `Kept`: ours stays and
the ledger records the foreign rev as seen, so it does not ask again; for a tombstone a ledger row
`(deleted, null)` beside a live row means "kept by decision"), `keep-right` (fast-forward/delete),
`keep-left` on a taken label or diverged article (`Skip`, cascading to what needs the row), and a
`same-work` decided `both` (insert). Rows of a source being merged are `Held` (counted, not written).

**What M4 needed next** (built, §7.7). (1) Dependent entities inside a matched pair (§5.4): findings (claim
fingerprints, Jaccard, period/point), assets by `(source, sha256)` with name clashes renamed,
attestation re-anchoring by quote/prefix/suffix when `body-differs` is `keep-right`, edges re-pointed
through the pair map and self-loops dropped, unresolved edges re-resolved; today they are counted as
held. (2) Articles: fast-forward when one body is a version the other already has
(`article_versions` by body hash), diff3 against a common base, `[[citekey]]` rewriting through the
id map. (3) Events, goals, places (with the `dataset` marker of §7.5), queues and aliases matched
beyond the uid. (4) `article_aliases` and a slug/event/goal rename path, so a renamed label is
followed instead of refused.

**What M5 needed next** (built, §7.7). `merge apply`: the backup, the plan-hash guard (`planHash` is computed with
the resolutions; re-plan and compare), and one transaction that folds each `map:`/auto-merge pair
into the surviving row field by field (the decisions are in `MergeIssue.decision`, the pair in
`SourceMatch`), writes `uid_aliases(foreign uid → local uid)` for the source AND each matched
dependent row, a ledger row per foreign uid with `local_uid` = the survivor and `local_rev_after` =
the survivor's rev as written (so `MergeState`'s id map makes the next import a plain uid import),
the foreign citekey as a `source_aliases` row (unless `alias-conflict`/`citekey-collision` say
otherwise), carries out `rename-right`/`rename-left` through `SourceRepo.renameIn`, and queues
`/review-article` for every article it changed. `BundleImport` already refuses exactly the decisions
M5 must add.

**Hang (#865).** The likely culprit of the hours-long run was munit itself: `munitTimeout` bounds only
asynchronous tests, so a synchronous body that blocks is never failed. The bundle, merge and planner
suites now mix in `Bounded` (each body on its own daemon thread, failed after `munitTimeout`). A
candidate for the hang itself, not changed here: `agents.Engine.worker` clears the thread's interrupt
after a run (`Thread.interrupted()` in `execute`'s `finally`), so an `ox` scope cancelled while a run
executes loses the interrupt and the worker blocks in `queue.take()` again — the scope's join then
never returns.

### 7.7 As built: M4–M5

**The survivor.** A pair that is one record folds into THIS collection's row: it keeps its uid, the
bundle's uid becomes a redirect (`uid_aliases`, one per folded record of any kind), and the ledger
row of the bundle's uid names the survivor as `local_uid` with the rev it was written with — so the
next plan of the same bundle finds every folded record by identity and changes nothing.
`map:<the bundle's citekey>` renames the survivor (through `SourceRepo.renameIn`; our citekey becomes
an old one); otherwise the bundle's citekey becomes an old citekey of the survivor, unless a
different record here has it (reported). The fields fold as §5.3 says (`core.MergeFold.foldSource`):
the richer title/authors/venue (more words, then more letters outside ASCII, then more capitals;
for authors, the longer written-out given names), unions of topics/regions (folded) and tags (the
`non-canonical-tag` normaliser), ISBNs and a DOI on one side, the larger citation counts, the
earliest `added_date`, the body and original of the side decided. Not built: the Crossref
version-of-record year (a ±1 year with one DOI keeps ours), and a three-way merge of summary/notes
from a ledger base (they ask, as M3 did).

**Inside a pair** (`MergePlanner`, stage 4; computed for every pair, an undecided one included, so
its questions are listed under its gate):

- *findings* (`MergeFold.matchFindings`, one to one): the same claim — folded words, stopwords out,
  sorted — at an overlapping time and the same place (points within `merge.finding-within-km`, or
  the same region name, or one says nothing) folds into ours: tags unioned, the narrower period and
  the point where only one has it, the more cautious confidence (a difference is reported). A claim
  whose words overlap at `merge.finding-near-at-least` (Jaccard) with overlapping periods is a
  `finding-near-duplicate` default `both` (`merge` folds it). The rest arrive as findings of the
  survivor, numbered after its last (`<citekey>#<n>`).
- *assets*: the same sha256 is one figure (one to one); a name taken in the survivor arrives as
  `<stem>-<label>.<ext>` — a hyphen, not the design's `~`, which is not link-safe in Markdown — and
  the `assets/<name>` references are rewritten when the merge keeps the bundle's body. An asset link
  a folded figure already has here is one.
- *attestations* (`core.TextAnchor`, the TextQuoteSelector): when the body the merge keeps is not
  the attestation's own, its quote is looked for exactly, then with every whitespace run read as
  one space (offsets mapped back to the text); several occurrences are told apart by how far the
  prefix and suffix agree, then by the old offset; its FTS chunk is recomputed
  (`limits.chunk-chars`, passed to the plan so plan and apply agree). Not found is a blocking
  `attestation-unanchored` (T) with `drop | keep-unanchored` — computed for this collection's
  attestations too (they move when the bundle's body is taken), under EACH alternative of the body
  question: the issues of the alternative not in force stay listed as conditional, because
  overriding a default is a decision as well and must not reveal a new issue (the conditions now
  chain: attestation → body → same work; only an explicit decision drops the other branch). A
  dropped attestation is recorded as deleted here (a tombstone), so the next plan does not bring it
  back. An attestation with the same target and quote as ours is one.
- *edges*: the survivor's out-set is ours plus the bundle's, re-pointed through every fold; a
  self-loop is dropped and reported. With one-to-one matching a self-loop only arises through a
  redirect named by an incremental bundle, so the plan now maps the uids a bundle REFERS to through
  the redirects and the ledger too (before, only its own). Parked edges are unioned and re-resolved
  as M2 does.

**Articles.** `MergeState` reads the recorded versions of every article the bundle names (sha256 and
body, newest first); the bundle's history is its `article-version` lines. For an article changed on
both sides (one uid) or one slug held by two articles (`MergeFold.articleOutcome`): the same text, or
the bundle's text among our versions → ours stays (ledger: seen); our text among the bundle's versions
→ fast-forward; else the newest of our versions the bundle's history also has is the base, and a
line-based diff3 (`core.Diff3`: LCS against the base on both sides, Khanna–Kunal–Pierce chunking)
merges it — clean: written, our text recorded as a version first, the bundle's text recorded as one
too (so the next merge finds it), and queued for review; conflicting: `changed-both-sides` or
`article-diverged` naming the number of conflicting regions, decided `keep-left | keep-right | both |
file:<path>`. `file:` sends the merged text along (`MergeResolution.text`: the CLI reads the file;
the server cannot), `both` writes the bundle's article as `<slug>-<label>` (a new record when the two
share a uid). `[[citekey]]` links in every article the bundle writes follow the merge — a merged
source's survivor citekey, a `rename-right`, a rename made over there — and the bundle's own text is
kept as a version when the rewrite changed it. Not built: rewriting the links of OTHER articles to a
`both` copy's new slug (the slug names ours).

**Events, goals, queue items.** An event under one id, dated alike and at the same place (`sameCore`)
on both sides — one uid changed on both, or two uids — is one: ours, with the datings (per
citekey), participants and outgoing edges of both. Otherwise `label-taken` (E) as M3, now with
`keep-right` (one event, the bundle's fields winning) and `rename-right:<id>`. A goal under one slug,
or changed on both sides, is always one: the status further along `planned → … → done`, the notes
joined under `### From <label>`, tags and regions unioned. An `acquire`/`tasks` item with the same
text (folded, whitespace collapsed) on both sides is one, `done` if done anywhere; a queue item
changed on both sides likewise; `log` entries are never matched. Not built: matching events across
two ids by label fingerprint and overlapping span, and places: no `dataset` marker was added and
local places still do not travel (they are reference data the schema cannot tell from a seed, and
carrying them needs new bundle files and entity kinds — a milestone of its own).

**Renames** (V042). Every foreign key into `articles(slug)`, `events(id)` and `goals(slug)` now
cascades on update, and `ArticleRepo.renameIn` (versions and outgoing links cascade, `[[old]]` in
other articles rewritten through the write path, link rows retargeted, the old slug kept in
`article_aliases`, which `GET /api/articles/{slug}` follows), `EventRepo.renameIn` (attestation and
asset-link targets follow) and `GoalRepo.renameIn` are what an import does when a record was renamed
over there — M3 refused that. There is no CLI rename for them yet, and `article_aliases` is not carried
in a bundle.

**Labels taken.** A finding id or an asset name a different record has here no longer asks
(`label-taken` F/X are gone): the bundle's finding is numbered after the last of its source, its
figure gets the label.

**`merge apply`** (`BundleImport.apply`, `POST /api/merge/apply`, `histmersion merge apply <file>
--resolutions … [--plan <hash>] [--yes]`; not an MCP tool). Inside one transaction and the import's
advisory lock: plan again with the resolutions; refuse — nothing written, no backup — on a problem, an
open issue, an unusable resolution, a `file:` without its text, or a plan hash that is not the one
given. The hash now also covers every row's decision and revs and the folds, so any change to a record
the merge would write changes it. A plan with nothing to change is a no-op (no backup, no write).
Otherwise the backup (`histmersion backup`'s `pg_dump`, synchronous — the request waits for it; a
failure refuses the apply), then M2's writes on the folded lines, then: tombstones of dropped
records, `updated_at = now()` on the rows the merge made (they are this collection's changes now),
the ledger, a `merges` row with `plan_hash`, `backup` and the resolutions (V043), `uid_aliases`, the
remembered decisions, a `/review-article <slug> --fix` task per article merged or changed by a
decision (three-way, `file:`, `both`, `keep-right` on a conflict) and a `log` entry. Last, the same
bundle is planned again inside the transaction: `replanChanges` is what it would still change — 0
when the merge is idempotent, which the tests require. `MergePlan.changes` counts the rows an apply
would write; after an apply it is 0, and a second apply is a no-op.

**Remembered decisions** (`merge_decisions`, V043 — git's rerere). Every decision a person made (a
resolution or a policy) that an apply carried out is kept by issue id; the next plan applies it to
the same issue (the same records, unchanged — the id hashes their revs) as `decided by remembered`,
after a resolution and a policy, before a default. A resolution whose issue is gone because an apply
carried it out is not stale. Without this a `keep-left` on a taken label or an old citekey (which
leave the bundle's record out, so nothing in the ledger remembers them) would ask again on every plan.

**Surfaces.** `merge apply` uploads the bundle, plans it with the resolutions (the files of `file:`
decisions read and sent), prints the plan and stops while anything is open, asks for confirmation
(or `--yes`) and applies the plan hash just shown — or the one given with `--plan`, from an earlier
review — and prints §6.4's report (`MergeMarkdown.applied`): sources new/merged/by score/renamed,
aliases, findings, figures, attestations moved and dropped, articles (three-way, fast-forward, both,
from a file), events/goals/queue merged, self-loops dropped, redirects, the review tasks, the log
entry, and the re-plan's count.

**What M6 needs next.** The `/merge` screen over the same `MergePlan` JSON: issues grouped, a
three-column field table per source pair, the diff3 hunks of an article (`Diff3.Result.marked` gives
them with markers), both geometries of an event on the map; decisions kept in a server-side
`merge_sessions` draft exportable as the resolution file; *Apply* posting `/api/merge/apply` with the
reviewed hash. **M7**: `histmersion-ingest`/`histmersion-query` skill text (present the plan, never
decide it), the plugin README row, and `merge list|show|ledger` (`GET /api/merges…`, MCP `merges`).

**Open questions found while building.** (1) M2 compares a source by its line's rev, which leaves the
edge set out: an edge-only change over there is not imported (a fold handles it — it compares the
edge sets). (2) A body fast-forwarded by M2 (one uid, changed only over there) does not move this
collection's own attestations of that source; only a merged pair re-anchors. (3) The backup inside
the request makes a large collection's apply slow to answer; a job (like `backup`) would fit. (4)
`both` on an article keeps the bundle's links to its slug pointing at ours. (Resolved since: (3) — the apply is a job, §7.8;
M6 and M7 as listed above are built, §7.8.)

### 7.8 As built: M6–M7 and the extras

**The review screen (M6).** `/merge` — the toolbar's merge button, or the address itself, which the
app turns into the window `merge` (`web.MergeView`); the bundle under review is in the URL
(`bundle=<sha256>`), so a reload reopens it. Upload through `POST /api/bundle/upload` (with progress);
the plan is `POST /api/merge/plan` with the decisions so far. The left column lists the issues
grouped — to decide, decided, conditional, defaults, information — with a counter ("3 of 9 to
decide") and a filter; the matched and new sources and the info lines fold under it. The right column
reviews the selected issue: both sides (here's citekey links to its page), the evidence (`why`), and
by entity — a source pair as a field table **here | result | bundle** (the rows that differ first;
the result column shows what the decision in force writes for the row the issue is about, "to
decide" until then — the other rows are folded by §5.3's rules on the server); an article as its
diff3 regions (agreed ones folded, each conflict as here | common version | bundle) and an editor
that starts from the text with git-style markers and refuses to "use this text" while a marker is
left; a place or an event as both geometries on a small Leaflet map (here and bundle in their own
colours) and both spans on one axis. The decisions are radio buttons (`map:` offers both citekeys;
`rename-*` takes a key, prefilled with the suggestion), with "take the decision back". To show any
of this the issue carries an optional **`detail`** (`IssueDetail`: fields, the texts and their hunks,
the geometries and spans), filled by the planner and — the geometries, hex EWKB in the plan — turned
into GeoJSON by `MergeState.withGeoJson` in one query; it is not part of an issue id or the plan
hash. **Not the design's**: the source's score "waterfall" is the evidence list the plan already has
(`why`), not a chart; the event's spans are a bar per side, not the main timeline.

Decisions live on the server: `merge_sessions` (V051), `GET`/`PUT /api/merge/sessions/{bundle}` — every
decision is kept, then the bundle planned again, so a decision shows at once what it settles and
which conditional issue it makes live. A successful apply drops the draft (its decisions are
remembered by issue id, `merge_decisions`). The draft has no MCP tool: it is the screen's, and an
agent presents a plan rather than deciding it (§7.4). The decisions download as the resolution file
(`MergeResolutionsText.template`, moved to `core/shared` so the browser writes the very file the CLI
does); a `file:` decision made on the screen has no file, so its text goes in a new **`texts:`**
section (a YAML literal block per issue id, chomping chosen so the text reads back exactly), which
`MergeResolutionsFile.parse` reads and `merge apply` (CLI) sends without opening a file. A file
written by the CLI uploads through `POST /api/merge/resolutions` (snakeyaml on the server, every bad
line at once), its decisions over the ones made so far. *Apply* sits behind the write key and a
confirmation naming the change count and the backup, posts the plan hash on screen, and follows the
job to the report (§6.4's numbers). Verified end to end in headless Chromium against two JVM servers
with databases of their own, seeded through the API: every issue of the crafted pair (same work with
a year gap and its conditional year, a status, a citekey collision, notes, an article both sides
changed in one paragraph, one event id for two events, one place id at two locations) decided in the
UI alone, the decisions downloaded, the same review at 400 px, applied with a real `pg_dump`, the
re-plan reporting nothing left.

**`merge list | show | ledger` (M7).** `GET /api/merges?limit=`, `GET /api/merges/{id}` (an id
prefix is enough), `GET /api/merges/ledger?uid=&merge=&limit=` (`uid`: a uid here or merged away, or
a citekey — its rows, those of every bundle uid redirected to it, and the redirects), the CLI's
`merge list | show <id> | ledger [--uid] [--merge]`, and the read-only MCP **`merges`** (`action:
list | show | ledger`). README, plugin README and the `histmersion-ingest` / `histmersion-query` skills
describe bundles and the merge (present a plan, never decide it).

**The apply as a job** (§7.7, open question 3). `POST /api/merge/apply` starts the apply — backup,
then the transaction — on its own thread (`MergeApplyJobs`, one at a time; a second is refused while
one runs) and waits `merge.apply-wait-seconds`; it answers `MergeApplyJob{job, state, result |
error}`, and `GET /api/merge/apply/{job}` follows it. The CLI polls. (As built since #888) the job is a
`jobs` row (kind `merge-apply`, V056 adds its `result`), so the poll survives a restart; an apply the
server stopped under is reported `interrupted` — the startup reconciliation fails every unfinished job,
and the apply's transaction committed or rolled back as a whole; `merges` says which.

**A collection's own places and periods** (§5.4, §7.5). V052 adds `dataset` (`seed` | `local`) to
`places` and `periods`: the seed paths (`init`, `places seed`, `places fetch`, `periods seed`,
`periods fetch`) write `seed`; `places import` / `periods import` (new) take `--dataset`, `local` by
default; the column default is `local`. Rows present when V052 runs are marked `seed` — they cannot be
told apart, and the conservative reading is that nothing starts travelling that did not before
(re-import a file of your own with `--dataset local`). A local row gets a change clock (its names,
parents, links and types move it) and a tombstone on delete. A bundle carries them
(`entities/places.ndjson` with names, parents, external ids and types; `entities/periods.ndjson`); the
manifest's reference fingerprint now covers the seed rows only. The plan matches a bundle place by
uid; else by id — the same geometry and span is one place (its names, links and types unioned), a
different one is **`place-differs`** (P, `keep-left | keep-right`, both geometries in the detail);
else by label (reduced as the gazetteer reduces it) and kind with points within
`merge.place-within-km` or shapes overlapping by `merge.place-overlap-at-least` (`MergeState.matchPlaces`,
PostGIS) — one place, and the bundle's events and child places are re-pointed to its id
(`MergePlanner.repoint`). Periods alike (`named-period-differs`, N; a match by label and an
overlapping span). An event whose place only the bundle carries is no longer a problem. (As built since
#888) a place renamed over there (a new id under one uid) is followed as a source rename is: the import
renames it here first (`PlaceRepo.renameIn`; V055 makes every key into `places(id)` cascade and keeps
the old id in `place_aliases`, which `GET /api/places/{id}` resolves). When the new id is another
place's here, ours keeps its id and what the bundle points at the new id points at ours; when this
collection's version of the place is kept (`keep-left`), the same. Place aliases do not travel.

**Events across two ids** (§5.4). A bundle event that would be new, whose id is free here, and a
local event the bundle does not carry with the same label fingerprint, outer spans sharing a year and
the same place (gazetteer id, or geometry, or neither placed) are one (`MergeFold.sameEventAcrossIds`):
folded into ours as an event under one id is (datings, participants, edges unioned), the bundle's uid
a redirect.

**Label renames** (§7.7). `POST /api/{articles|events|goals}/{label}/rename` `{"to": …}`, the CLI's
`article|events|goals rename <old> <new>`, the MCP `rename_label`. V053 `event_aliases`/`goal_aliases`
keep an old event id or goal slug resolving (as `article_aliases` does for slugs): a read by the old
label answers with the record, a write to it goes to the record under its new label and says so
(#1799 — it used to be refused; one rule for every renamed kind, `core.Aliases`), renaming back drops the
alias, an old label of another record is not free. An article rename also moves the goal links that
name it. **Old article slugs travel** (`entities/article-aliases.ndjson`); one that is a slug or an
old slug of another article here is left out and said. **So do old event ids, goal slugs and place
ids** (#890: `entities/event-aliases.ndjson`, `goal-aliases.ndjson`, `place-aliases.ndjson`, the
places' only for the places a bundle carries — this collection's own): the same rule, one that is a
label or an old label of another record here is left out and said. A rename over there is followed by
uid, and the old labels it left behind keep resolving here as they do there. `POST
/api/places/{id}/rename` (`places rename <old> <new>`, `rename_label` with `kind: place`) renames a
place here (V055); a `place=<old id>` filter (`PlaceRepo.scopeFor`) stands for the place it names now.

**Goals' plans, links and schedules (#881).** V054 adds `goals.plan_approved_at` (set with the plan)
and makes a goal's links and schedules move its clock (a schedule only by `cron`, `enabled` or its cap
— not a scheduler tick). The goal line gains `plan`, `planApprovedAt`, `budgetUsd`, `links` (a linked
source, article or queue item by uid; a queue item's `ref` is its uid, its serial id being local) and
`schedules` (kind, cron, on/off, cap — never the run state), all absent when empty so older lines keep
their rev. Merge rules: links are unioned; this collection's schedules are kept and a difference is
reported, a schedule of a kind we lack is brought in **without a next run** ("save it once here to
schedule it": a merged collection never starts agent runs by itself); the plan approved later wins,
two different plans with no order are **`goal-plan-differs`** (G, `keep-left | keep-right`); our run cap
unless we have none. A link's label follows a merge (the survivor's citekey).

**Open questions found while building.** (1) The geometry match of places uses the representative
`places.geom`, not the dated footprints. (2) A place folded into a seed row keeps that row `seed`, so
names the merge added to it do not travel on. (3) `places import` of a file whose ids collide with
seed rows turns them `local` (the last import says whose a row is). (4) ~~The apply job registry is
per run~~ — resolved by #888: the job is a `jobs` row and a restart reports it `interrupted`.

## 8. Test strategy

- **Crafted fixture pair**, built through the API into two `*_test` databases, with one case per row of
  the scenario catalogue: author order swapped; `Güterbock, Hans G.` vs `H. G. Guterbock`; `Harrison,
  Jane Ellen, 1850-1928` vs `Jane Harrison`; subtitle present or absent; title case vs sentence case;
  reprint (1959 vs 2024); online-first ±1; DOI on one side; identical sha256 with conflicting DOIs;
  same text with different sha256 (MinHash path); same citekey for different works; an alias on one
  side equal to a citekey on the other; near-duplicate findings; an edge pointing at each side's own
  key; an article with a common base (clean and conflicting diff3) and without one; an attestation
  that cannot be re-anchored; a local place with the same id and different geometry; a queue item
  done on one side only; a deletion (tombstone). The expected plan is a golden file. The test asserts
  **the whole issue set in one run**.
- **Property tests (ScalaCheck)**:
  - round trip `import(export(db)) ≅ db`;
  - idempotency `merge(merge(A,B),B) = merge(A,B)`;
  - ids: `plan` is deterministic, and issue ids are stable under re-ordering of the input NDJSON;
  - monotone resolution: adding a decision never adds a non-conditional issue;
  - no dangling edges, and no finding without a source, after any apply;
  - symmetry on conflict-free inputs: `merge(A,B)` and `merge(B,A)` give isomorphic graphs (modulo
    labels).
- **Matcher quality:** a labelled pair set from the live corpus. The 49 `source_aliases`, the
  `duplicate-doi` and `duplicate-sha256` lint history, and the rename log give true matches. Random
  same-author pairs give non-matches. Report precision and recall per threshold, and tune `merge.weights`.
- **Fault injection:** truncated bundle, bad checksum, schema newer than the server (refuse), crash
  mid-apply (the transaction rolls back; the ledger stays unchanged).

## 9. Open questions

1. Should a reprint and its first edition ever be two sources? This is a corpus-wide policy rather
   than a merge question. The `editions` policy flag defers it.
2. Should an imported article be marked `unreviewed` until `/review-article` passes? That would need
   an `articles.review_state` column, which the gate does not have today.
3. Should `unmerge` be supported, using the ledger, or is the pre-apply backup enough?
