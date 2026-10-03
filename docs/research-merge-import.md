# Research: merging two Histmersion collections (portable export + dedup merge-import)

**Status:** research and design proposal (2026-10-03). Nothing here is built. The figures describe the
live corpus on that date, read with `histmersion query`. Statements I could not check are marked
**(unverified)**.

## 0. The problem in one paragraph

Someone keeps one Histmersion at work and another at home. Both start from the same bundled seeds
(Pleiades/Cliopatria/Natural Earth places, PeriodO presets), and both then grow on their own: sources,
OCR bodies, findings, typed edges, articles with version history, assets, events, attestations, goals,
the three queues, Calibre links and rename aliases. Later the user wants **one** collection. The same
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
| strong keys | DOI (lowercased, `https://doi.org/` stripped), ISBN-13 (normalised from ISBN-10), OpenAlex W-id, original-file sha256 | new `source_identifiers(source_uid, scheme, value)`, unique on `(scheme, value)` for doi/isbn/openalex |
| instance-scoped keys | `calibre_id` means something only in **one** Calibre library: work's `1801` is not home's `1801` | `calibre_links(source_uid, library_uuid, calibre_id)`. Calibre's `metadata.db` has a `library_id` table holding a uuid **(unverified; check before relying on it)** |
| labels | citekey / slug | as now; `source_aliases` covers renames **and** merges; a new `article_aliases` covers slugs |
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

## 4. Export format: the `.hmx` bundle

A **zstd-compressed tar** (or a plain directory when given `--dir`), deterministic so that two exports
of an unchanged database are byte-identical:

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
entities/calibre_links.ndjson    # with library_uuid
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
references other entities **by uid** (`citekey` fields are kept for readability only). Left out:
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
| calibre link | both kept in `calibre_links`, one per library uuid. The local library stays canonical |
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

- **V029__instance_and_uids.sql**: `instance(id uuid pk default uuidv7(), label text, created_at)`;
  `uid uuid NOT NULL DEFAULT uuidv7() UNIQUE` and `origin uuid` on the tables listed in §3, back-filled
  to the local instance; `tombstones(uid, kind, deleted_at)` maintained by the delete paths.
- **V030__identifiers.sql**: `source_identifiers` (back-filled from `doi`, `sha256`), `calibre_links`
  (back-filled from `calibre_id` + the configured library), `article_aliases`.
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
| M0 | identity: V029–V030, uids in DTOs and `get_source`, tombstones | every row has a uid; `rename` keeps it | 2–3 d |
| M1 | `bundle export/verify`, deterministic, blobs, `--since` | two exports of an unchanged database are byte-equal | 3 d |
| M2 | import with **no** matching: into an empty instance, and back into its own origin | export → import into an empty instance → export is byte-equal; importing your own bundle changes 0 rows | 2–3 d |
| M3 | source matcher + `merge plan` + resolution file + stable issue ids | crafted fixture (§8) gives exactly the expected issue list in one run | 5–7 d |
| M4 | dependent entities: findings, edges, assets, attestation re-anchoring, articles diff3, events, places, queues, aliases | every row of the §5.4 table has a fixture | 6–8 d |
| M5 | `merge apply`: backup, plan-hash guard, ledger, report, review tasks | apply, then re-plan → 0 changes | 3 d |
| M6 | web `/merge` review screen | resolve the fixture's issues in the UI alone | 4–6 d |
| M7 | MCP `merge_plan`/`merges`, skill text (`histmersion-ingest`), README rows | — | 1 d |

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
| Calibre: two libraries | `calibre_links` per library uuid. The merge never writes to Calibre. `calibre gaps` afterwards shows what to push |
| Seed drift: work and home seeded different Pleiades snapshots | the manifest records seed versions. A mismatch is *info*, and seed places are compared by `rev` only when the versions match |

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
4. Is the Calibre `library_id` uuid reachable from `metadata.db` **(unverified)**? If not, ask the user
   to label each library once.
