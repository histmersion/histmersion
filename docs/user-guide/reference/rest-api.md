# REST API

The server's HTTP API, under `<public-url>/api`. The web page, the CLI and the MCP tools all use it.
Reads are open; when `auth.api-key` is set, every `POST`, `PUT`, `PATCH` and `DELETE` needs
`Authorization: Bearer <key>` (401 otherwise).

From [`modules/core/shared/src/main/scala/com/histmersion/core/Api.scala`](https://github.com/histmersion/histmersion/blob/master/modules/core/shared/src/main/scala/com/histmersion/core/Api.scala) —
the single definition the server interprets and the CLI derives its client from.

| method | path | query / body | returns |
|---|---|---|---|
| `GET` | `/api/version` · `/api/health` · `/api/config` | — | build info · db + schema version · the `ui` config block the page reads, plus `writeKeyRequired` (whether writes need the API key — never the key) and `baseMapSource` resolved (`local` or `online`, never `auto`: [Working offline](../how-to/offline.md)) |
| `POST` | `/api/config/reload` | — | `ReloadReport{published, generation, applied[], restartOnly[], rebuilt[], plugins[], draining[], errors[], warnings[], file}` — the config file read again and applied in place (see [Reloading the config](configuration.md#reloading-the-config-no-restart)); a failed reload is a 200 whose `errors` say why nothing changed |
| `GET` | `/api/about` | — | `About{name, build{version, gitCommit, commitDate, dirty}, health, jobs{queued, running, done, failed}, counts{sources, findings, articles, events, places, periods, presets}, lastBackup, lastBackupAgeSeconds, problems[], configFile{path, loaded, unknownKeys}, plugins{spiVersion, plugins[], warnings[], set{digest, jars[], mode, kit, linkedAt}}, storage{measuring, measuredAt, tookMillis, ageSeconds, entries[{key, label, group, path, bytes, files, exists, partial, within, detail, shrink, volume, host}], tables[{name, bytes}], volumes[{id, totalBytes, freeBytes}], backups{count, bytes, newest, keep}, lowFreeBytes, hints[], problems[]}}` — the web UI's About panel and `histmersion about`; a part that cannot be read is a `problems` line, never an error. `storage` is the last background measurement (`storage.*` settings; `measuring` while one runs): an entry `within` another is counted there, not again |
| `GET` | `/api/sources` | the **filter params** (below) | `SearchResult{total, hits[{source, matched[], why[]}], offset, next?}` — the source is the unit; `next` is the offset of the next page (absent on the last) |
| `GET` | `/api/facets` | `field`, `prefix` + filter params | `[{value, count}]` (distinct sources, case-folded) |
| `GET` | `/api/findings` | filter params (+ legacy `region`), `limit`, `offset` | `[GeoHit]` — the map layer; antimeridian bbox split server-side |
| `GET` | `/api/findings/page` | as `/api/findings` | `GeoHitPage{total, hits[GeoHit], offset, next?}` — one page of the same findings with the paging fields: what MCP `geo_when` and `histmersion geo-when` print |
| `GET` | `/api/aggregate` | `by=time\|region\|time,region\|region,time`, `unit=sources\|findings\|starts`, `depth`, `asOf`, `samples` + filter params | `TimeHistogram{from, to, bucket, buckets[], by, unit, total, depth, asOf, tree[]}` — `buckets` is the flat strip (`by=time`), `tree` the §1.6 nested `Bucket{label, key, kind, start, end, placeId, count, overlap, contested, children[], samples[]}` forest |
| `GET` | `/api/sources/{citekey}` | `body` (default `true`; `false` omits `ocrMarkdown`) | `SourceWithFindings` (404 if unknown) |
| `GET` | `/api/sources/{citekey}/articles` | — | `[Backlink{kind, id, title}]` — the compiled articles that CITE this source (404 if the citekey is unknown) |
| `PUT` | `/api/sources/{citekey}` | `SourcePut` body; `strict` | full create/replace; `SourceWithFindings{…, formatting[]}` |
| `PATCH` | `/api/sources/{citekey}` | `SourcePatch` body (`ifUpdatedAt`); `strict` | selective merge; 404 / 412 / 400; the answer's `formatting[]` lists the summary's and notes' formatting problems, and `strict=true` refuses (400) a patch whose summary/notes have an error |
| `GET` | `/api/sources/{citekey}/lint` | — | `FormatReport{subject, issues[FormatIssue{rule, severity, field, line, column, excerpt, message, fix}], fixed[]}` — the summary's and notes' formatting problems |
| `POST` | `/api/sources/{citekey}/lint/fix` | — | apply the wording-safe repairs (one patch, guarded by the `updatedAt` just read) and report what is left |
| `GET` | `/api/sources/{citekey}/export` | — | the note as text (frontmatter + notes) |
| `POST` | `/api/sources/{citekey}/rename` | `{"newCitekey": …}` | `SourceRenamed{oldCitekey, newCitekey, source, counts{findings, articles[], sources[], links, columns[], edgesPromoted}, calibreId}` — one transaction; the old citekey becomes an alias (reads and writes naming it go to the new one); 400 when the new key is taken or not link-shaped, 404 when the old one is unknown |
| `POST` | `/api/sources/{citekey}/delete` | `{"apply": false}` | `SourceDeletion{citekey, title, applied, blockers[{kind, id, title}], counts[{part, rows}], aliases[], edgesIn[], coverage[], files[], filesFailed[], mentions[], calibreId, logEntry}` — a **dry run** unless `apply`; applied, one transaction: other sources' edges to it are parked (not lost), its old citekeys go, a tombstone keeps a bundle merge from bringing it back, the log queue records it, and the stored original is removed after the commit. 400 while an article cites it (`[[ck]]`, an old citekey or a short form), an event is sourced to it or a job is still writing it — there is no override. Calibre is never touched; `calibreId` is the record's stored link — `histmersion delete` looks it up in the host's library (read-only) and says whether the entry is still there. Not an MCP tool (a person's decision, like deleting an article) |
| `GET` | `/api/source-aliases` | — | `[{alias, citekey}]` — every old citekey and the source it names now |
| `GET` · `PUT` | `/api/instance` | `PUT`: `{"label": …}` | `InstanceInfo{id, label, createdAt}` — this collection's identity (see [Portable bundles](../how-to/backup-and-bundles.md#portable-bundles)) |
| `GET` | `/api/bundle` | `since` (an ISO-8601 moment) | the `.hmx` bundle, streamed (`Content-Disposition` names it) |
| `POST` | `/api/bundle/verify` | the `.hmx` file as the body | `BundleVerifyResult{ok, manifest, blobsChecked, problems[]}` |
| `POST` | `/api/bundle/import` | `dryRun`; the `.hmx` file as the body | `BundleImportResult{bundleId, from, dryRun, applied, counts[], blobs, problems[], conflicts[], notes[], mergeId, issues[], planHash}` — planned with no resolutions |
| `POST` | `/api/bundle/upload` | the `.hmx` file as the body | `BundleUpload{sha256, bytes, manifest, problems[]}` — kept server-side under its sha256 |
| `POST` | `/api/merge/plan` | `MergePlanRequest{bundle: <sha256>, resolutions{resolve[{issue, decision}], policy{editions}}}` | `MergePlan{matches, sources[], issues[], info[], problems[], resolutionErrors[], planHash}` — read-only; also MCP `merge_plan` |
| `POST` | `/api/merge/import` | `MergeImportRequest{bundle, resolutions, dryRun}` | `BundleImportResult` — refused while an issue is open or a decision needs `merge apply` |
| `POST` | `/api/merge/apply` | `MergeApplyRequest{bundle, planHash, resolutions}` (a `file:` decision carries its `text`) | `MergeApplyJob{job, state (running · done · failed), bundle, planHash, elapsedMs, result: MergeApplyResult{applied, mergeId, backup, counts[], tally, reviews[], logEntry, replanChanges, problems[], issues[]}, error}` — a background job (its backup first): the request waits `merge.apply-wait-seconds`; refused (nothing written, no backup) while an issue is open or when the plan is not `planHash`; not an MCP tool |
| `GET` | `/api/merge/apply/{job}` | — | `MergeApplyJob` — where an apply is: `running`, `done` (its report), `failed`, or `interrupted` by a restart (a `jobs` row, so it answers across restarts; 404 for an unknown job) |
| `GET` · `PUT` | `/api/merge/sessions/{bundle}` | `PUT`: `MergeResolutions` | `MergeSession{bundle, resolutions, updatedAt}` — the `/merge` screen's decisions so far for an uploaded bundle, kept server-side (dropped by a successful apply) |
| `POST` | `/api/merge/resolutions` | a resolution file (YAML) as the body | `MergeResolutions` — the file read (every bad line reported at once); writes nothing |
| `GET` | `/api/merges` | `limit` | `[MergeInfo{id, bundleId, bundleSha256, from, appliedAt, planHash, backup, ledgerRows, redirects}]` — newest first; also MCP `merges` |
| `GET` | `/api/merges/ledger` | `uid` (a uid or a citekey), `merge`, `limit` | `MergeLedger{query, entries[LedgerRow{foreignUid, kind, mergeId, foreignRev, localUid, label, localRevAfter, decision, recordedAt}], redirects[]}` |
| `GET` | `/api/merges/{id}` | an id or its first characters | `MergeDetail{merge, applied, imported, resolutions, remembered[]}` (404 if unknown) |
| `POST` | `/api/articles/{slug}/rename` · `/api/events/{id}/rename` · `/api/goals/{slug}/rename` · `/api/places/{id}/rename` | `{"to": …}` | `LabelRenamed{entity, from, to, rewritten[]}` — the old label stays an alias: reads AND writes follow it (a put, patch, delete or rename naming it goes to the record under its new label, and the answer names that label; an attestation or asset link targeting it is stored against the new one), and a `place=<old id>` filter matches the place it names now |
| `POST` | `/api/sources/import` | `{citekey?, text, mtime?, force?}` | seed ONE note from raw text (host-side `seed`) |
| `GET` | `/api/citekeys` | — | every citekey, sorted |
| `GET` | `/api/neighbors/{citekey}` | — | `[NeighborRow]` |
| `POST` | `/api/seed` | `{dir, force}` | imports a **server-side** directory; rows newer than the file are skipped unless `force` |
| `POST` | `/api/edges/resolve` | — | promote unresolved edges whose target now resolves (exactly or by unique short form) to a stored source |
| `POST` | `/api/reindex` | `after`, `limit` | one resumable slice of the FTS chunk rebuild |
| `POST` | `/api/ingest` | `citekey`, `filename?`, `quality?`, `force?`, `title?`, `author*`, `year?`, `doi?`, `acquireItem?` + the document as the **raw body** | **202** `IngestAccepted{jobId, citekey, status, sha256}`; **409** when the same bytes are already stored under another citekey (unless `force`); **400** when `acquireItem` is not an open acquisition item. With `acquireItem`, the OCR job ticks that item off (`ingested as [[citekey]]`) when it succeeds and leaves it open when it fails. Without it, the job (of every import: an upload, `ingest <doi>`, `archive import`, `calibre import`) closes the open acquisition items that name the source's DOI — any case, resolver prefix or trailing punctuation — the same way, and lists items whose text resembles the title and first author (`acquire.defaults.title-match-at`) in its `progress` as possible matches, never closing them; a re-OCR matches nothing |
| `POST` | `/api/sources/{citekey}/reocr` | `quality` | 202 + a job; converts the spooled original again |
| `GET` | `/api/jobs/{id}` · `/api/jobs` | `status`, `limit`, `before`, `upTo` | `JobInfo` / `[JobInfo]` — `queued\|running\|done\|failed` — newest first (`created_at`, then id); **paged by keyset**: `before=<id>` = the next older page after that row, `upTo=<id>` = every row down to and including it; the body stays an array and the headers carry `X-Total-Count` (rows matching the filters), `X-Next-Before` (the cursor of the next older page, absent on the last) and `X-State-Counts` (`done=12,failed=3`: every state's count, over the other filters but not the state filter) |
| `GET` | `/api/sources/{citekey}/assets` | — | `[AssetInfo{name, mime, bytes, sha256, width, height}]` |
| `POST` | `/api/sources/{citekey}/assets` | `[AssetInput{name, mime, base64, …}]` | add figures to an existing source (host-side `seed`) |
| `GET` | `/source/{citekey}/assets/{name}` | — | the figure's bytes, with its `Content-Type` and `Cache-Control: public, max-age=31536000, immutable` |
| `GET` | `/api/media-geo` | `kind`, `from`, `to`, `limit`, `provider` (`source` = a source's figures, `wikimedia-commons`, `met-museum`, `cleveland-museum`, `art-institute-chicago`, `library-of-congress`), `license` (an SPDX id, `public-domain`, `none`) | `[MediaGeoHit{citekey?, assetName, kind, lat, lon, periodStart, periodEnd, caption, uid, provider?, license?, credit?, thumbUrl?}]` — the map's media layer |
| `GET` | `/api/media` | `provider`, `license`, `place`, `event`, `q`, `limit`, `offset` | `MediaList{total, items[MediaItem], offset, next?}` — stored pictures, source figures and providers' alike, newest first, each with its licence, credit, author, page, original URL, retrieval time and the places/events it shows |
| `GET` | `/api/media/facets` | — | `MediaFacets{providers[], licences[]}` — pictures per provider and per licence (`none` = no licence): the media layer's filter menus |
| `GET` | `/api/media/providers` | — | `[MediaProviderInfo{name, title, over, description, plugin, builtin, licences}]` — what `media import` can run on this server: the media collections its plugins registered, the built-in ones first (a plugin's with the `media.collections` allowlist the server judges it by). REST only, like the import (no MCP tool: an import is an operator's decision) |
| `GET` | `/api/media/{uid}` | — | `MediaItem` (404 if unknown) |
| `POST` | `/api/media/import` | `MediaImportRequest{provider: "wikidata", dryRun = true, limit?, place?, event?, refresh}` — `provider` one of `wikidata`, `depicts` (Commons files depicting events: P180, P1299), `met`, `cleveland`, `aic` (museum objects, CC0 only, tied to a place by find-spot and dates), `loc` (public-domain historical maps), `pleiades`, `opencontext`, or a plugin's collection (`/api/media/providers`; an unknown name or one whose plugin is switched off is a `400` saying so); see [docs/media-providers.md](https://github.com/histmersion/histmersion/blob/master/docs/media-providers.md) | `MediaImportReport{targets, items, statements, files, byLicence[], byRole[], rejected[], stored, updated, linked, categories, revoked[], failed[], samples[{targetType, targetId, targetLabel, key, title, license, basis, url}]}` — `samples`: a collection's (or the depictions') matches with the reason each is tied to its place, for a person to judge. For `wikidata`: asks Wikidata about the places that hold findings (most first) and the events with a Wikidata item, not asked yet unless `refresh`, and reads each file's licence and author from Commons; a dry run (the default) stores nothing. A real run here is ONE `place` or `event` (over many: `400`, use the job below). One import at a time |
| `POST` | `/api/media/import/job` | `MediaImportRequest{dryRun: false, limit?, place?, event?, refresh}` | `202 JobInfo` — the import as a job (`histmersion jobs`): the places on the map (those holding findings) ONE per Wikidata item, sites first, then by sources citing and findings, `media.import-batch` items at a time, each batch recorded before the next so a stopped job resumes; `limit` caps the whole job (none = every one not asked yet). Its progress is `n/total asked · batch k · stored · linked · failed`; a picture that cannot be fetched or stored is counted in the summary, not fatal; the summary ends with how many are still not asked |
| `POST` | `/api/media/covers` | `MediaCoversRequest{dryRun = true, limit?, citekey?, refresh, covers = true, illustrations = true, isbn?}` | `MediaCoversReport{dryRun, sources, covers, illustrations, lines[{citekey, role, provider, detail}], skipped[], failed[]}` — a dry run (from the database alone), or one `citekey` done at once: an Open Library cover by the record's ISBN/OCLC (or `isbn`), and an illustration chosen among the source's own figures (the largest on its early pages; never a logo, a masthead strip, a running header or a page scan) |
| `POST` | `/api/media/covers/job` | `MediaCoversRequest` | `202 JobInfo` — the same over every source, as a job |
| `PUT` | `/api/media/sources/{citekey}/cover` | `provider=calibre`, `key` (the Calibre id), `mime`; the image as the body | `MediaItem` — a cover the CLI read from the local Calibre library, stored as the source's cover (provider `calibre`, no reuse licence: shown locally, never in a bundle) |
| `POST` | `/api/media/fill-licences` | `apply` | `FigureLicenceFill{candidates, updated, applied, bySource[]}` — source figures with no licence take their source's (`sources.license`); without `apply` the count only |
| `GET` | `/media/{uid}/thumb` · `/media/{uid}/file` | — | a picture's stored thumbnail / rendition from the database, with its `Content-Type` and `Cache-Control: public, max-age=86400` — the cards never reach the provider (a source's figure or cover without a thumbnail is served as itself) |
| `GET` | `/api/articles` | filter params + `kind`, `text` | `ArticleList{total, articles[]}` — the compiled wiki: the articles CITING at least one source the filter selects (every article when it selects nothing); `title` is the article's own title, `text` FTS over the article's title/summary/body; `limit`/`offset` page the articles |
| `GET` | `/api/events` | filter params + `text`, `kind` (a kind or `natural` · `human`), `historicity`, `placeId`, `tier` (`attested` · `catalogue`), `context` (also the catalogue events of the period, whatever the sources), `area` (`minLon,minLat,maxLon,maxLat`, the event's anchor) | `EventList{total, events[], offset, next?}` — the events whose source (its own, or one attesting it) the filter selects (its period aside), whose OWN span overlaps `from`/`to`; `title` is the label, `text` a label/description substring, `placeId` the event's own place; each `EventSummary` carries `scale` (`local` · `regional`) and `scaleStated` (false: derived from the kind, the event's own geometry, the place's kind, a parent), and `precision` (`precise` · `approximate` · `imprecise` · `undated`, `core.EventPrecision`) with the catalogue's stated `central` year, `plusMinus` and `datingMethod` — an imprecise event is listed here, never drawn on the map |
| `GET` | `/api/events/geo` | the same params | `EventGeoList{events[{id, label, kind, scale, historicity, timeSpan, lat, lon, anchor, placeId, placeLabel, parentId, outline, tier, size, precision, dating}], unplaced, imprecise}` — the map's events layer (#1449: `tier` attested · catalogue, `size` the catalogue's VEI or magnitude) and playback feed (#1440): those events with a position — the event's own point or area, else its place's (an area by its point-on-surface; `anchor`: `event` · `place`) — a REGIONAL one with an area carrying its outline as GeoJSON simplified to `ui.events.outline-tolerance-deg`, at most `ui.events.map-limit`; `unplaced` counts the rest. Only DATED events precise enough to stand at a year: one dated ± more than 500 years (V088's `events.uncertainty`: the stated ±, or half the widest uncertain window) is left out and counted in `imprecise`; an `approximate` one (± 50–500) is drawn faded. A web feed like `/api/places/geo`: no MCP tool |
| `POST` | `/api/events/import` | `EventImportRequest{provider: gvp·noaa·wikidata·a plugin's catalogue, dryRun = true, limit?, bbox?, from?, to?, minVei?, minMagnitude?, refresh}` | `202 JobInfo` — read a catalogue as a job (#1449): a dry run stores nothing and leaves `EventImportReport{records, kept, created, merged, matched, byKind[], skipped[], parts, pending, licence, citation}` on the job (`detail.eventImport`); a real run merges each part with the events there (one event per thing: same catalogue key, Wikidata item, or kind + overlapping time + `events.merge-km` — never two records of one catalogue; a compiled event is matched, never overwritten; an import's own event is refreshed from its own record, the corrections by hand kept — `corrections{kept, retired, conflicts}` in the report) and records the part, so a stopped import resumes. Each record keeps the catalogue's stated year, ± and dating method and its own record as read (`raw`); its span is held before the present and, for the GVP, after `events.gvp.floor-year` (a plugin's catalogue: after its own `floorYear`, and a record stating only a year ± is dated `year ± n`, as the GVP's are) |
| `POST` | `/api/events/rederive` | `EventRederiveRequest{provider?, dryRun}` | `202 JobInfo` — compute the stored records' dating again from what `event_catalogue.record` keeps, with NO network (a record stored before V088 is read back from its span, its method from its description), and refresh the events the catalogues created, corrections kept (a plugin catalogue's records are dated exactly as its import dated them, so an import followed by a rederive changes nothing); leaves `EventRederiveReport{records, changed, fromRaw, updated, before[], after[], offMap, examples[], corrections}` on the job (`detail.eventRederive`). REST and CLI only: a bulk rewrite is an operator's step, as the import |
| `GET` | `/api/events/corrections` | `status` (`active` · `agreed` · `conflict`; default all but `agreed`), `event`, `limit`, `offset` | `CorrectionList{total, corrections[{id, targetKind, targetId, field, value, upstreamValue, status, upstream, upstreamNow, label}]}` — the fields of catalogue events corrected by hand (V088), conflicts first; also MCP `event_corrections`, `histmersion events corrections` |
| `GET` | `/api/events/catalogues` | — | `[CatalogProviderInfo{id, title, licence, citation, home}]` — the catalogues `events import` can read on this server: those its plugins registered, the built-in ones first |
| `GET` | `/api/events/leads` | as `/api/events` (`tier` defaults to `catalogue`, `context` to true) | `EventLeadList{total, leads[{eventId, label, kind, tier, years, text, actions[{kind, label, command, query?}], findingsNear, candidates}]}` — catalogue events no source discusses, largest first |
| `POST` | `/api/events/candidates` | `EventCandidatesRequest{all, event?, limit?}` | `202 JobInfo` — compute the proposed links of the events not checked yet (or all) |
| `GET` · `POST` | `/api/events/{id}/links` | POST: `EventLinksRequest{recompute, confirm[], reject[]}` | `EventLinkList{eventId, label, candidates[{citekey, score, status, findings[], passages[{start, end, quote, name}], reasons[]}]}` / `EventLinksResult{links, attested[], refused[], rejected[]}` — a confirmation writes an attestation only from a passage of the source's body that names the event |
| `POST` | `/api/site-types/refine` | `SiteTypesRequest{dryRun, limit?, refresh}` | `202 JobInfo` — refine the type of the Wikidata sites on the map (with a finding or a picture) from their items' P31 classes, walked up P279 through the curated table (`core.SiteTypes`) |
| `GET` | `/api/events/{id}` | — | `EventDetail{event, geometry, parent, children[], datings[], edges[], participants[], wikidata?, media[], tier, names[], catalogue[], sources[], findings[], articles[], candidates[], leads[], corrections[]}` — the card's context (#1449): the catalogues listing it with licence and citation, the sources attesting it, the findings near it, the articles citing its sources, the sources possibly discussing it (unconfirmed) and its leads; (404 if unknown; an old id answers as the event it was renamed to) — `media` is the event's pictures (`MediaThumb{uid, role, url, credit, license, licenseUrl, sourceUrl, width, height}`) |
| `PUT` | `/api/events/{id}` | `EventPut` body (`scale`: `local` · `regional`, absent = derived) | full create/replace; a `scale` that is none is a 400 |
| `PATCH` | `/api/events/{id}` | `EventPatch` body (`scale`: `local` · `regional` · `auto` = clear; `wikidata`: `Q…`, `""` = clear) | selective; also MCP `patch_event` and `histmersion events patch`. On an event a catalogue import created, each changed field (label, kind, timeSpan, historicity, description, geometry) is recorded as a correction (V088) and kept across re-imports: retired once the catalogue says the same, flagged `conflict` when it changes to something else. A `timeSpan` set by hand drops the catalogue's stated year and ± |
| `GET` | `/api/articles/{slug}` | `brief` | `ArticleView{article, backlinks[], sourcesCited[], formatting[], coverage?}` (404 if unknown) — `formatting` lists what in the body and summary will not display as written; `coverage` is present when the article records a filter; `brief=true` leaves both out (the link preview's read) |
| `GET` | `/api/articles/{slug}/coverage` | — | `Coverage{filter, filterLine, total, checked, cited[], notUsed[{source, reason?}], citedOutside[], statuses[], edges[], searches[], reasons[], drift[]}` — the recorded filter against the live corpus, from which every surface renders the generated Not used list and `_Method_` note; 404 when the article records no filter |
| `PUT` | `/api/articles/{slug}` | `ArticlePut` body; `strict` | full create/replace; answers the `ArticleView` (formatting included); `strict=true` refuses (400) a body/summary with a formatting error |
| `PATCH` | `/api/articles/{slug}` | `ArticlePatch` body (`append`, `ifUpdatedAt`, `filter`, `reasons`); `strict` | selective merge; 404 / 412 / 400; `strict=true` judges the article as patched |
| `GET` | `/api/articles/{slug}/lint` | — | `FormatReport` of the body and summary (see [Formatting lint](formatting-lint.md)) |
| `POST` | `/api/articles/{slug}/lint/fix` | — | apply the wording-safe repairs as one guarded patch (the old body stays a version) and report what is left |
| `DELETE` | `/api/articles/{slug}` | — | drops the article and its links |
| `GET` | `/api/articles/{slug}/export` | — | the note as text (`wiki/<kind>/<slug>.md`): the filter and reasons in the frontmatter, the generated sections after the body between markers an import drops |
| `POST` | `/api/articles/import` | `{text, slug?, kind?, mtime?, force?}` | import ONE article note from raw text (host-side `seed`) |
| `GET` | `/api/article-slugs` | — | every slug, sorted |
| `GET` | `/api/lint` | `rule`* (repeatable) | `LintReport{rules[{rule, description, count, items[{citekey, detail}]}]}`; unknown rule → 400 |
| `POST` | `/api/lint/fix` | `rule`* (repeatable) | the safe repairs (resolve short edge targets, reindex stale chunks), bounded per call — loop while `remaining > 0`; a named gazetteer rule applies its repair too (`place-imported-point-area` bounded per call as well — loop while `pointAreasRemaining > 0`) |
| `POST` | `/api/lint/markdown` | `FormatCheck{text, field = body\|summary\|notes, fix}` | `FormatReport` for Markdown not stored yet; with `fix`, `text` is the repaired text |
| `GET` | `/api/queue/{list}` | `all` | open items of `acquire`\|`tasks`\|`log` (`all=true` adds done items and the file's prose blocks) |
| `POST` | `/api/queue/{list}` | `{text, topic?, status?, createdAt?}` | append one item |
| `PATCH` | `/api/queue/items/{id}` | `{status?, text?, topic?, reason?}` | selective edit; `status=done` stamps `done_at`; a blank `topic`/`reason` clears it, a blank `text` is a 400; 404 if unknown |
| `DELETE` | `/api/queue/items/{id}` | — | remove one item, returning it |
| `GET` | `/api/queue/{list}/export` | — | the list rendered as its `meta/<FILE>.md` |
| `POST` | `/api/queue/{list}/import` | `{text, replace=true}` | parse a `meta/*.md` back into rows (idempotent) |
| `GET` | `/api/export/goals` | — | every research goal as `meta/GOALS.md`: the tree (sub-goals nested), status, period/regions/tags, notes, links (articles and sources as `[[…]]`, searches, queue items with their done/open state), schedules and approved plan. Written by `queue export` / `export` beside the queue files; export only, `queue import` ignores it |
| `GET` | `/acquire` · `/tasks` · `/log` | — | the web UI with that list's window open (the toolbar's "to acquire", "backlog" and "worklog"). Acquire and tasks items can be added, edited, deleted and ticked off there; an acquisition item can be fulfilled by **Upload…** (the upload form, filled in from the item; the upload form can also pick an item itself) or **Link existing source…**. Either ticks it off as `ingested as [[citekey]]`: a link at once, an upload when its OCR job succeeds. The worklog, newest date first, is added to only. Every change needs the API key when one is set |
| `POST` | `/api/query` | `{sql}` | `QueryResult{columns, rows, rowCount, truncated, elapsedMs}`; a write is a 400 carrying Postgres's refusal |
| `GET` | `/api/schema/tables` | — | the live `information_schema` shape (tables, views, columns) |
| `GET` | `/api/agents/models` | — | `ModelsInfo` — each step role's chain, the model its next call would use, every allowance's use per window and when it frees (no call is made) |
| `PUT` · `DELETE` | `/api/agents/models/{role}` | `{models: ["provider:model", …]}` (PUT) | `RoleChain` — the role's chain saved over `agents.roles.<role>` (works with agents off); DELETE goes back to config |
| `PUT` | `/api/agents/allowances` | `{target, limits: [{amount, measure: tokens\|usd, window}], reservePct}` | `AllowanceStatus` — one model's (`provider:model`) or provider's (`provider:*`) allowance, saved over `agents.allowances`; no limit = unlimited |
| `DELETE` | `/api/agents/allowances` | `target` | `{removed}` — config's allowance (if any) applies again |
| `POST` | `/api/agents/generate` | `{runtimes?: [claude-code\|codex\|opencode], out?}` | `GenerateResult{dir, runtimes, files, written, removed, definitions}` — writes the per-runtime agent trees ([design](https://github.com/histmersion/histmersion/blob/master/docs/research-agent-runtimes.md#22-the-generator)) on the SERVER's disk: under the runtimes directory, or `out`. Works with agents off |
| `GET` | `/api/agents/quota` | — | `QuotaInfo{plans[], metered[], fallthrough[]}` — each subscription pool's windows (used, left, floor, reset, reading age and source), hold, verdict; the metered allowances; the roles whose chain may fall from a plan to a paid key |
| `PUT` | `/api/agents/quota/{pool}` | `{hold?, floors?}` | `PoolStatus` — hold or release `claude-plan` / `chatgpt-plan`; save its floors (`""` = config's) |
| `POST` | `/api/agents/quota/refresh` | `{pool?}` | `QuotaRefreshed{pools: [{pool, refreshed, detail, status}]}` — a fresh reading of one pool (or every pool) now, whatever its age: the ChatGPT plan through `codex app-server` (spends nothing), the Claude plan through one minimal `claude -p` turn on `probe-model` (spends a little of the plan; not with `refresh = off`); a pool without a login is not read |
| `PUT` | `/api/agents/quota/fallthrough/{role}` | `{fallthrough}` | `QuotaInfo` — may that role's chain fall from a plan below its floor to a paid key |
| `POST` | `/api/agents/quota/claude` | `{source, rateLimits: [{kind, percentUsed, resetsAt}]}` | `GuardAnswer{allow, reason, resetsAt}` — the Claude quota-guard mod's report; a headless run's mod sends its RUN TOKEN, which this route (and only this route) accepts where writes otherwise need the API key |
| `GET` | `/api/agents/doctor` | `offline` | `DoctorReport` — every provider checked without spending anything: key present, the free `GET …/models` listing (skipped `offline`), chained models listed upstream and priced, the `claude` CLI, its login method and plan (never the account), the loopback rule |
| `POST` | `/api/agents/probe` | `{citekey, model?}` | `ProbeResult` — one live call offered one read tool (`get_source`) with a typed answer, checked against the record |
| `POST` | `/api/agents/runs` | `{pipeline, target, model?, budgetUsd?, budgetTokens?, dryRun?, modelFor?[{role, model}]}` | `RunInfo` — the queued run; refused (400) when agents are off, a metered model is unpriced, a provider has no key, or a `modelFor` role is unknown, given twice or not used by the pipeline. `dryRun`: every read and model step runs, nothing is written to the corpus (`RunInfo.dryRun`; a resumed dry run stays one) |
| `GET` | `/api/agents/runs` · `/api/agents/runs/{id}` | `status`, `limit`, `goal`, `before`, `upTo` | `[RunInfo]` (paged and counted like `/api/jobs`) · `RunDetail{run, steps, messages}` — steps are the checkpoints, with tokens and cost per attempt, the configured `model` and the `actualModel` the provider reported, and a dry run's skipped writes (`wouldWrite[{what, payload}]`) |
| `GET` | `/api/agents/compare` | `id` (repeated, ≥ 2; one pipeline, one target) | `Comparison{pipeline, target, runs[], steps[{name, runs[{models, actual, billing, usage, costUsd, seconds, attempts, failures}]}], enrich[], review[], overlaps[], notes[]}` — enrich-source: findings, quote check, places, dated findings, period, summary/notes length, claims only one run made; review-article: verdict, rounds, round 1 per reviewer and kind, shared major findings |
| `GET` | `/api/agents/approvals` | `state` (`open` by default, `approved\|rejected\|failed\|all`), `limit`, `before`, `upTo` | `[ApprovalInfo]` (paged and counted like `/api/jobs`; cursors are approval ids) |
| `POST` | `/api/agents/runs/{id}/{pause\|resume\|cancel}` | `budgetUsd`, `budgetTokens` (resume) | `RunInfo`; 409 when the run is in the wrong state |
| `GET` · `POST` | `/api/agents/managers` | — · `ManagerCreate{kind, goal?, config?, paused?, cron?}` | `[ManagerInfo{id, kind, goal, state, pauseRuns, config, effective, statusNote, cron, nextTickAt, counts, inFlight, lastEvent, spentTodayUsd, tokensToday, backlog, backlogDays}]` · the new manager: `review` (id `review`) and `enrich` (id `enrich`) library-wide, `seed` with its `goal` (id `seed:<goal>`, starting with a propose-articles item), `watch` with its `goal` (id `watch:<goal>`, cron `agents.managers.watch.cron` unless given, one item per sweep); created **paused**. `spentTodayUsd` = its runs' metered spend this UTC day (a plan-billed call costs 0 USD), `tokensToday` every call's tokens; a watcher's `backlog` = the legal open-access works waiting in its ingest backlog (`oa-ingest` items) and `backlogDays` the days they take to drain at the current allowance (0 = today, absent = they do not drain) |
| `GET` · `PUT` · `DELETE` | `/api/agents/managers/{id}` | — · `ManagerUpdate{config?, cron?}` · — | `ManagerDetail{manager, current[ManagerItem], hints, events}` · `ManagerInfo` (the config is replaced as given; an absent key is the server's `agents.managers.<kind>.*`; `cron` five fields in UTC or `@daily`…, `""` clears it, never more often than `agents.scheduler.min-interval-minutes`; the enrich manager has none) · `Removed` (its runs in flight are cancelled) |
| `POST` | `/api/agents/managers/{id}/{pause\|resume\|stop}` | `runs` (pause: its runs in flight too) | `ManagerInfo` — pause starts no new run; resume also resumes the runs it paused; stop cancels its runs in flight and its open items |
| `GET` · `POST` | `/api/agents/managers/{id}/items` | `status`, `offset`, `limit` · `EnqueueRequest{kind, target, priority?}` | `ItemPage{items[ManagerItem], total, offset, limit}` (the most urgent first, each with its run) · `ManagerItem` (a settled one is re-opened) |
| `PATCH` | `/api/agents/managers/{id}/items/{item}` | `ItemPatch{action: hold\|release\|priority\|skip\|reopen, priority?, until?, untilSource?, reason?}` (`item` = id or target) | `ManagerItem`; 409 in the wrong state |
| `GET` · `POST` · `DELETE` | `/api/agents/managers/{id}/hints` · `…/hints/{hint}` | `all` · `HintAdd{text, item?, once?, effect?{action: prioritise\|hold\|skip\|config, targets, tag, topic, goal, until, untilSource, priority, config}, propose?, suggest?}` | `[ManagerHint{…, proposal, proposed}]` · `ManagerHint` (a person's effect applied at once; with `propose` — a model's — it becomes a `hint-effect` approval, applied only when a person approves it, and `config` is allowed only so; `suggest` starts a `hint-effect` run whose model proposes the action the same way; its text reaches the next runs' fixer and skeptic) · `Removed` (retired) |
| `GET` | `/api/agents/managers/{id}/events` | `before`, `limit` | `EventPage{events[ManagerEvent], before}` — newest first |
| `POST` | `/api/agents/enqueue` | `EnqueueRequest{kind: review\|extend\|enrich\|seed, target, priority?}` | `EnqueueResult{managerId, items[ManagerItem], skipped}` — a page button: `review`/`extend` an article (the review manager; extend = compile its filter's new sources, then review), `enrich` a source or the unenriched sources an article cites or a goal links (the enrich manager), `seed` a goal (its seeding manager proposes its next articles); a missing manager is created paused; priority 10 by default |
| `GET` · `POST` | `/api/agents/autopilot` | — · `AutopilotSet{on, by?}` | `AutopilotStatus{on, since, by, agentsEnabled, managersEnabled, approvals[{kind, mode, cap}], ingestedToday, ingestCap, maxConcurrentRuns, lastBackup, lastLint, library[ManagerBrief], goals[GoalProgress{slug, title, status, parent, managers, sources, articles, openApprovals, lastActivity, doneLast7, open, etaDays}], changes, backlog, backlogDays}` — the autopilot (docs/research-process-managers.md "As built: the autopilot"): on, every unfinished goal gets an active seeding manager (a plan-goal item first when it has no plan) and a watcher with legal open-access auto-ingest, the goal statuses move from the facts, and a backup and the safe lint repairs run on their cadence; off pauses the seeding managers and watchers it runs (on again resumes exactly those). Idempotent; `changes` = what the switch just did; `backlog` / `backlogDays` = the watchers' ingest backlog and the days to drain it at the current allowance |
| `GET` · `POST` | `/api/agents/autopilot/focus` | — · `FocusSet{action: pin\|unpin\|priority\|max, goal?, value?, by?}` | `FocusInfo{on, max, maxFrom: config\|person, active[FocusGoal{slug, title, status, stage, why, pinned, priority, since, doing, open, doneLast7, etaDays}], queue[FocusGoal], queued, waiting[{slug, code: children\|person\|held\|stuck\|idle\|plans, why, pinned}], held, note, changes}` — the autopilot's focus (docs/research-process-managers.md "As built: the focus"): at most `max` goals are active for model work, the others' model-using items held with the reason `focus`. GET answers what the next managers' tick decides (readable with the autopilot off: what it would focus on); POST pins or unpins a goal, sets its priority, or the cap (`max`; no `value`: back to `agents.autopilot.max-active-goals`); `stage` = reviewing · compiling · ready · gathering. `AutopilotStatus` carries the same as `focus` |
| `POST` | `/api/agents/autopilot/repair-acquire` | `RepairRequest{dryRun?, by?}` | `RepairResult{dryRun, moved, removed, kept, untouched, rows[{item, doi, goals, watcher, action, detail, backlogItem}], backlog, backlogDays}` — the one-shot repair of the acquisition list: every OPEN acquisition item a watcher's sweep created (found by structure — the sweep run that ranked the DOI, the time its `queue` step ran, the goal link it made) for a work the ranking marked open access moves into that watcher's ingest backlog and leaves the list (`move`; its goal links go with it), one the corpus has meanwhile leaves it (`remove`), the others stay (`keep`, with why). A person's items, a plan's and a currency check's are never touched (`untouched`). `dryRun` changes nothing |
| `GET` | `/api/places` | `q`, `asOf`, `limit` | `[PlaceHit]` — gazetteer search by name/label, valid at `asOf` (the **time rule**, below); each row says how much of its period the source states (`dating`: `stated`, `end-only` — start unknown, matched only in `places.sources.open-start-window-years` before the end — or `unknown`) and who brought it in (`importSource`) |
| `GET` | `/api/places/{id}` | `asOf`, `counts` | `PlaceDetail` — geometry as **GeoJSON text**, parents/children as of `asOf` (404 if unknown; an old id of a renamed place answers with the place under its id now); `counts=true` adds `counts{sources, findings, articles, events}` — what `place=<id>` selects (the map's hover card) — and `media[MediaThumb]`, the place's pictures from outside providers, best first, with their credit and licence — its own and its equivalents' (the same Wikidata item, an old id merged into it, another gazetteer's point of the same name within `media.same-site-km`); `hasBoundary` (a polygon — a region or polity without one is drawn as a label, "point only — boundary not yet available"), `descriptions[{text, lang, source}]` (Wikidata's description, English first) |
| `GET` | `/api/places/geo` | `kind`, `asOf`, `limit`, `from`, `to`, `pick` | a GeoJSON `FeatureCollection` for the map: places of `kind` with a shape, valid at `asOf` (the time rule) — or, with `from`/`to`, read as `pick` says: `mid` (default), `start`, `end` (a snapshot at that year) or `earliest`/`latest` (every state alive at any time in the range, one shape each: its earliest or latest version there; versions grouped by normalised label; docs/map-layers.md); an area's geometry is its stored web outline (simplified to a hundredth of its size, at most 0.02°, V090); `limit` is capped by `places.geo-max-limit`; each feature's `properties` carry `id, label, kind, periodStart, periodEnd, dating, hasBoundary` and `basis` — why a view at a time shows it (`dated`, `timeless`, `modern`, `findings`; `findings` is drawn dashed, "dated by its findings") |
| `GET` | `/api/places/{id}/children` | `asOf` | `[PlaceSummary]` — with `asOf`, only the children the time rule shows |
| `GET` | `/api/places/{id}/tree` | `asOf`, `depth` | `[PlaceTreeRow]` — the subtree, depth-tagged (the indented list MCP prints); with `asOf`, the time rule decides each node |

**The time rule** (`asOf` on the place endpoints above; `PlaceTimeBasis`): a view at a time shows what
was there then. A place whose own period overlaps the year is shown (`dated`; an unknown start uses its
stored window); a physical feature — a seed place of kind `feature` or a Natural Earth physical region —
at any time (`timeless`); a present-day place (`properties.modern`: the Natural Earth cities, undated
GeoNames/Wikidata places) only from `places.modern-from` on (`modern`); an undated place with no shape
(a continent, a Natural Earth subregion) as a tree node (`grouping`); any other undated place — a
curated region, an undated Pleiades site, a Natural Earth country — only when a finding or an event
dated within the window is attached to it (`findings`; its card says "dated by its findings"). Without
`asOf` nothing is filtered. The map across all time draws no present-day place, and an undated
GeoNames/Wikidata place is on no time-sliced map.
| `GET` | `/api/places/names` | `place` | `[ManualPlaceName{placeId, label, name, lang}]` — the names added by hand (every place's, or one's) |
| `POST` | `/api/places/{id}/names` | `{"name": …, "lang": …}` | `PlaceNameChanged` — a further name, stored as **manual**: an import replaces a place's other names but never these, so a re-seed keeps them |
| `POST` | `/api/places/{id}/names/remove` | `{"name": …, "lang": …}` | `PlaceNameChanged` — only a name added by hand (a seed name is refused) |
| `GET` | `/api/places/overrides` | `place` | `[PlaceOverride{placeId, label, kind, periodStart, periodEnd, periodSet, reason, sourceKind, sourcePeriodStart, sourcePeriodEnd, updatedAt}]` — the kinds and periods set by hand, with what each source says |
| `POST` | `/api/places/{id}/override` | `PlaceOverrideSet{kind?, periodStart?, periodEnd?, reason?, reset}` | `PlaceOverrideChanged` — a kind and/or period set by hand over the source's (one side of the period alone changes that side); the database applies it over every later import (V080), and the place's findings are matched again at once; `reset` restores the source's |
| `POST` | `/api/places/{id}/merge` | `{"into": …, "reason": …}` | `PlaceGone` — the place is `into`: findings, links, children, events, attestations and asset links move, it is deleted, its id resolves to `into`; no seed import re-creates it |
| `POST` | `/api/places/{id}/remove` | `{"reason": …}` | `PlaceGone` — delete a place nothing refers to (refused while an event, attestation, asset link, child, hand-added name or a finding with no other place does); no seed import re-creates it |
| `POST` | `/api/places` | `PlaceAdd{label, kind, lon, lat, source, id?, names[], lang?, periodStart?, periodEnd?}` | `PlaceSummary` — a point place of this collection's own (`dataset = local`, id `local_<label>` by default); `source` is required; refused when the id is taken |
| `POST` | `/api/places/import` | `kind`, `dataset` (`local` — the default: this collection's own, carried in a bundle — or `seed`) + an **LPF or GeoJSON FeatureCollection** body | upsert; `{places, names, relations, failed}` |
| `POST` | `/api/places/rebuild` | — | re-materialise `finding_places`; `{links, byGeometry, byName, located, unlocated, places}` |
| `GET` | `/api/acquire/discover` | `q`, `since`, `sort`, `limit`, `notInDb` | `DiscoverResult` — OpenAlex search, hits marked `inDb` |
| `GET` | `/api/acquire/meta` | `id` (DOI) | `WorkRef` — normalised metadata (OpenAlex, Crossref fallback) |
| `GET` | `/api/acquire/wikirefs` | `url` | `WikirefsResult` — the DOIs a Wikipedia article cites |
| `POST` | `/api/acquire/refs` | `{id, apply}` | `RefsResult`; `apply` writes `references` edges |
| `POST` | `/api/acquire/assess` | `{id, apply}` | `Assessment`; `apply` patches `status` |
| `POST` | `/api/acquire/retractions` | — | `RetractionsUpdate` — refresh the Retraction Watch mirror |
| `POST` | `/api/acquire/followups` | `{id, since, limit, apply}` | `FollowupsResult` with a proposed edge kind per citing paper |
| `POST` | `/api/acquire/ingest` | `{id, citekey?, arxiv?}` | `IngestReport` — metadata → OA PDF → job → edges → assessment |
| `GET` | `/api/acquire/archive/search` · `/info` | `q`/`id` | archive.org items and their downloadable files |
| `POST` | `/api/acquire/archive/import` | `{id, citekey?, format?}` | `IngestReport`; lending-only items are refused |
| `GET` | `/api/acquire/calibre/gaps` | `limit` | `CalibreGaps` — stored sources not in Calibre yet |
| `POST` | `/api/acquire/calibre/pushed` | `{citekey, calibreId}` | record a host-side Calibre push |
| `GET` | `/source/{citekey}` | `full`, `fragment`, `q` | the article page (HTML); `q` highlights matches, figures render as lazy `<img>` with a click-to-zoom lightbox. An old (renamed) citekey answers **301** to the current one |
| `GET` | `/article/{slug}` | `fragment`, `q` | the compiled article page (HTML): `[[citekey]]` → `/source/…`, `[[slug]]` → `/article/…`, plus backlinks and a "Sources cited" footer |
| `POST` | `/mcp` | JSON-RPC | the MCP tool surface |

**Filter params** (shared by `/api/sources`, `/api/facets`, `/api/findings`, `/api/aggregate`, `/api/articles`, `/api/events`, and mirrored one-to-one by the CLI flags and the web UI URL hash): `q`, `tag`*, `author`*, `place`*, `topic`*, `status`*, `yearFrom`, `yearTo`, `from`, `to`, `minLon`, `minLat`, `maxLon`, `maxLat`, `sort` (`relevance|cited|period|year|title`), `limit`, `offset`, `title` (`*` = repeatable). `title` is a case-insensitive substring of the LISTED item's own title — a source's, an article's, an event's label. On `/api/articles` and `/api/events` the other params select SOURCES, and the list is what relates to them (one rule, the `/api/sources` predicate as a sub-select), so the web UI's Articles and Events tabs follow the same filter as its Sources tab.

**Offset paging** (`core.OffsetPage`): every offset-paged list — `/api/sources`, `/api/events`, `/api/goals`, `/api/attestations`, `/api/asset-links`, `/api/media`, `/api/findings/page` — answers `{total, …, offset, next?}`: `total` ignores `limit`/`offset`, `next` is the offset of the next page and is absent on the last. The Markdown every MCP tool and CLI command prints heads a cut list `### 50 of 412 event(s)` and ends it `_showing 51–100 of 412 — next: offset=100_`. The newest-first lists (jobs, agent runs, approvals) page by keyset instead (`before=<id>`, the `X-Next-Before` header).

**Grouping** (plan §1.6, `/api/aggregate` only, on top of the filter params): `by` (`time` — the default — or `region` / `time,region` / `region,time`), `unit` (`sources` — the default — `findings`, or `starts`: sources counted ONCE, in the bucket their period *begins* in, which is what the web UI's timeline strip draws — counting every overlapped bucket makes a corpus of long-range syntheses look flat), `depth` (1–3; `0` = `ui.tree-depth`), `asOf` (signed year the gazetteer hierarchy is read at; `time,region` ignores it and reads each time bucket at its own midpoint), `samples` (0–5 sample sources per leaf, default `ui.tree-samples`).

`by=time` **with no `depth`/`samples` at all** is the flat §1.7 strip and nothing else — that is what the timeline asks for on every span change, and it must not pay for a tree it does not draw. Any other combination fills `tree`. Deeper levels are fetched lazily by re-asking the same query with `from`/`to` narrowed to a time bucket, or `place=<gazetteer id>` set to a region node — a single resolved `place` **roots the region tree at that node**, so the answer is its subtree rather than the whole forest.

A `place=` value that is a **gazetteer id** (`GET /api/places?q=…`) matches through the materialised
`finding_places` *and the hierarchy as of the window* — so `place=continent_asia&from=-1500&to=-1200`
finds a Hattusa dig through Turkey. Anything else is matched as a name against
`sources.regions ∪ findings.region_name`, exactly as before the gazetteer existed, and the two are
OR'ed so seeding places can only ever add hits. `/api/facets?field=places` reports the `id` behind a
label so a chip can filter by it, and `contested: true` for a hand-traced shape whose extent is
disputed. See [`db/places/README.md`](https://github.com/histmersion/histmersion/blob/master/db/places/README.md) for the seed data and its licences.

`POST /api/ingest` takes the document as the **raw request body** rather than a multipart part:
tapir's netty backend does not implement multipart request bodies (`RawBodyType.MultipartBody` throws),
so the file is the body and everything else is a query parameter. The CLI derives its uploader from
that same endpoint definition.

`from`/`to` are signed years. `POST /api/seed` resolves `dir` **on the server**; an empty `dir` means
`corpus.dir`, which `just install` sets from `HISTMERSION_CORPUS_DIR`.

## Every endpoint

The index of [`Api.all`](https://github.com/histmersion/histmersion/blob/master/modules/core/shared/src/main/scala/com/histmersion/core/Api.scala) — generated,
so it lists every endpoint the server defines even where the table above has no row of its own yet.

<!-- BEGIN API INDEX (generated by `just api-docs` — do not edit by hand) -->
237 endpoints on 199 paths:

| methods | path |
|---|---|
| `GET` | `/api/about` |
| `POST` | `/api/acquire/archive/import` |
| `GET` | `/api/acquire/archive/info` |
| `GET` | `/api/acquire/archive/search` |
| `POST` | `/api/acquire/assess` |
| `GET` | `/api/acquire/calibre/gaps` |
| `GET` | `/api/acquire/calibre/meta` |
| `POST` | `/api/acquire/calibre/pushed` |
| `GET` | `/api/acquire/discover` |
| `POST` | `/api/acquire/followups` |
| `POST` | `/api/acquire/ingest` |
| `GET` | `/api/acquire/meta` |
| `POST` | `/api/acquire/refs` |
| `GET` · `POST` | `/api/acquire/retractions` |
| `GET` | `/api/acquire/wikirefs` |
| `GET` | `/api/agent-state` |
| `GET` · `PUT` · `DELETE` | `/api/agent-state/{key}` |
| `PUT` · `DELETE` | `/api/agents/allowances` |
| `GET` | `/api/agents/approvals` |
| `POST` | `/api/agents/approvals/{id}/{decision}` |
| `GET` · `POST` | `/api/agents/autopilot` |
| `GET` · `POST` | `/api/agents/autopilot/focus` |
| `POST` | `/api/agents/autopilot/repair-acquire` |
| `GET` | `/api/agents/compare` |
| `GET` | `/api/agents/doctor` |
| `POST` | `/api/agents/enqueue` |
| `POST` | `/api/agents/generate` |
| `GET` · `POST` | `/api/agents/managers` |
| `GET` · `PUT` · `DELETE` | `/api/agents/managers/{id}` |
| `GET` | `/api/agents/managers/{id}/events` |
| `GET` · `POST` | `/api/agents/managers/{id}/hints` |
| `DELETE` | `/api/agents/managers/{id}/hints/{hint}` |
| `GET` · `POST` | `/api/agents/managers/{id}/items` |
| `PATCH` | `/api/agents/managers/{id}/items/{item}` |
| `POST` | `/api/agents/managers/{id}/{action}` |
| `GET` | `/api/agents/models` |
| `PUT` · `DELETE` | `/api/agents/models/{role}` |
| `POST` | `/api/agents/probe` |
| `GET` | `/api/agents/quota` |
| `POST` | `/api/agents/quota/claude` |
| `PUT` | `/api/agents/quota/fallthrough/{role}` |
| `POST` | `/api/agents/quota/refresh` |
| `PUT` | `/api/agents/quota/{pool}` |
| `GET` · `POST` | `/api/agents/runs` |
| `GET` | `/api/agents/runs/{id}` |
| `POST` | `/api/agents/runs/{id}/{action}` |
| `GET` | `/api/agents/schedules` |
| `GET` | `/api/aggregate` |
| `GET` | `/api/article-slugs` |
| `GET` | `/api/articles` |
| `POST` | `/api/articles/import` |
| `GET` · `PUT` · `PATCH` · `DELETE` | `/api/articles/{slug}` |
| `POST` | `/api/articles/{slug}/check` |
| `GET` | `/api/articles/{slug}/coverage` |
| `GET` | `/api/articles/{slug}/export` |
| `GET` | `/api/articles/{slug}/lint` |
| `POST` | `/api/articles/{slug}/lint/fix` |
| `POST` | `/api/articles/{slug}/rename` |
| `GET` | `/api/articles/{slug}/versions` |
| `GET` | `/api/articles/{slug}/versions/{versionId}` |
| `GET` · `POST` | `/api/asset-links` |
| `DELETE` | `/api/asset-links/{id}` |
| `GET` · `POST` | `/api/attestations` |
| `GET` · `PATCH` · `DELETE` | `/api/attestations/{id}` |
| `POST` | `/api/backup` |
| `GET` | `/api/backup/status` |
| `GET` | `/api/backups` |
| `GET` | `/api/backups/{file}` |
| `GET` · `DELETE` | `/api/basemap` |
| `POST` | `/api/basemap/fetch` |
| `GET` | `/api/basemap/styles` |
| `POST` | `/api/basemap/styles/check` |
| `GET` | `/api/basemap/styles/example` |
| `GET` | `/api/basemap/{layer}/{z}/{x}/{y}` |
| `GET` | `/api/bundle` |
| `POST` | `/api/bundle/import` |
| `POST` | `/api/bundle/upload` |
| `POST` | `/api/bundle/verify` |
| `GET` | `/api/citekeys` |
| `GET` | `/api/config` |
| `POST` | `/api/config/reload` |
| `GET` | `/api/contradictions` |
| `POST` | `/api/edges/resolve` |
| `GET` | `/api/events` |
| `POST` | `/api/events/candidates` |
| `GET` | `/api/events/catalogues` |
| `GET` | `/api/events/corrections` |
| `GET` | `/api/events/geo` |
| `POST` | `/api/events/import` |
| `GET` | `/api/events/leads` |
| `POST` | `/api/events/rederive` |
| `GET` · `PUT` · `PATCH` · `DELETE` | `/api/events/{id}` |
| `GET` · `POST` | `/api/events/{id}/links` |
| `POST` | `/api/events/{id}/rename` |
| `GET` | `/api/export/goals` |
| `GET` | `/api/facets` |
| `GET` | `/api/findings` |
| `GET` | `/api/findings/page` |
| `GET` | `/api/goals` |
| `GET` · `PUT` · `PATCH` · `DELETE` | `/api/goals/{slug}` |
| `POST` · `DELETE` | `/api/goals/{slug}/links` |
| `POST` | `/api/goals/{slug}/rename` |
| `PUT` · `DELETE` | `/api/goals/{slug}/schedules/{kind}` |
| `GET` | `/api/goals/{slug}/view` |
| `GET` | `/api/graph/{citekey}` |
| `GET` | `/api/health` |
| `POST` | `/api/ingest` |
| `POST` | `/api/init` |
| `GET` · `PUT` | `/api/instance` |
| `GET` | `/api/jobs` |
| `GET` | `/api/jobs/{id}` |
| `GET` | `/api/lint` |
| `POST` | `/api/lint/fix` |
| `POST` | `/api/lint/markdown` |
| `POST` | `/api/maintenance/vacuum` |
| `GET` | `/api/media` |
| `GET` | `/api/media-geo` |
| `POST` | `/api/media/covers` |
| `POST` | `/api/media/covers/job` |
| `GET` | `/api/media/facets` |
| `POST` | `/api/media/fill-licences` |
| `POST` | `/api/media/import` |
| `POST` | `/api/media/import/job` |
| `GET` | `/api/media/providers` |
| `PUT` | `/api/media/sources/{citekey}/cover` |
| `GET` | `/api/media/{uid}` |
| `POST` | `/api/merge/apply` |
| `GET` | `/api/merge/apply/{job}` |
| `POST` | `/api/merge/import` |
| `POST` | `/api/merge/plan` |
| `POST` | `/api/merge/resolutions` |
| `GET` · `PUT` | `/api/merge/sessions/{bundle}` |
| `GET` | `/api/merges` |
| `GET` | `/api/merges/ledger` |
| `GET` | `/api/merges/{id}` |
| `GET` | `/api/neighbors/{citekey}` |
| `GET` | `/api/periods` |
| `POST` | `/api/periods/fetch` |
| `POST` | `/api/periods/import` |
| `POST` | `/api/periods/seed` |
| `GET` | `/api/periods/{id}` |
| `GET` · `POST` | `/api/places` |
| `POST` | `/api/places/build-hierarchy` |
| `POST` | `/api/places/fetch` |
| `GET` | `/api/places/geo` |
| `POST` | `/api/places/import` |
| `POST` | `/api/places/load` |
| `GET` | `/api/places/names` |
| `GET` | `/api/places/overrides` |
| `POST` | `/api/places/rebuild` |
| `GET` | `/api/places/sources` |
| `GET` | `/api/places/{id}` |
| `GET` | `/api/places/{id}/children` |
| `GET` | `/api/places/{id}/footprints` |
| `GET` | `/api/places/{id}/links` |
| `POST` | `/api/places/{id}/merge` |
| `POST` | `/api/places/{id}/names` |
| `POST` | `/api/places/{id}/names/remove` |
| `POST` | `/api/places/{id}/override` |
| `POST` | `/api/places/{id}/remove` |
| `POST` | `/api/places/{id}/rename` |
| `GET` | `/api/places/{id}/tree` |
| `POST` | `/api/query` |
| `PATCH` · `DELETE` | `/api/queue/items/{id}` |
| `GET` · `POST` | `/api/queue/{list}` |
| `GET` | `/api/queue/{list}/export` |
| `POST` | `/api/queue/{list}/import` |
| `POST` | `/api/reindex` |
| `GET` | `/api/resolve/{id}` |
| `POST` | `/api/restore` |
| `GET` | `/api/schema/tables` |
| `GET` | `/api/search` |
| `POST` | `/api/seed` |
| `POST` | `/api/site-types/refine` |
| `GET` | `/api/source-aliases` |
| `GET` | `/api/sources` |
| `POST` | `/api/sources/import` |
| `GET` · `PUT` · `PATCH` | `/api/sources/{citekey}` |
| `GET` | `/api/sources/{citekey}/articles` |
| `GET` · `POST` | `/api/sources/{citekey}/assets` |
| `PATCH` | `/api/sources/{citekey}/assets/{name}` |
| `GET` | `/api/sources/{citekey}/assets/{name}/detail` |
| `POST` | `/api/sources/{citekey}/delete` |
| `GET` | `/api/sources/{citekey}/export` |
| `GET` | `/api/sources/{citekey}/lint` |
| `POST` | `/api/sources/{citekey}/lint/fix` |
| `GET` | `/api/sources/{citekey}/original` |
| `GET` | `/api/sources/{citekey}/passages` |
| `POST` | `/api/sources/{citekey}/rename` |
| `POST` | `/api/sources/{citekey}/reocr` |
| `GET` | `/api/sources/{citekey}/view` |
| `GET` | `/api/ui/counters` |
| `POST` | `/api/ui/seen/{board}` |
| `GET` | `/api/version` |
| `GET` | `/iiif/{citekey}/{name}/info.json` |
| `GET` | `/iiif/{citekey}/{name}/{region}/{size}/{rotation}/{filename}` |
| `GET` | `/media/{uid}/file` |
| `GET` | `/media/{uid}/thumb` |
| `GET` | `/source/{citekey}/assets/{name}` |
<!-- END API INDEX -->
