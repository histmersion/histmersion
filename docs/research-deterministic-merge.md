# Research: deterministic, order-independent merging of reference data

Status: design and audit, 2026-10-28. No code has changed. Line numbers refer to `master` at
`93331581`. Live counts come from read-only queries against the running server on 2026-10-28.

Scope: the **reference data**: places and sites, their names, links, types and shapes; events
from catalogues; pictures from media providers; and the person and agent edits made on top of
them. The sources come from Pleiades, Natural Earth and the curated seed, GeoNames, Wikidata, Getty
TGN, LPF datasets, PeriodO, the GVP, NOAA, Wikidata events and media providers. Planned additions are
OpenStreetMap historic and heritage POIs, Wikidata heritage designations (P1435) and national heritage
registers as plugins. Scholarly content (sources, findings, articles, goals, queues) stays with the
merge planner of `docs/research-merge-import.md`. A person decides those merges, and this document
does not change them. Periods (PeriodO, `periods`) fit the same model, but this audit does not cover
their code line by line.

---

## 0. Summary

- **Today the result depends on import order.** The gazetteer joins a record to "the place one of
  its ids already names". The first source to bring a place in owns its id, label, kind, shape and
  period. Every later source only adds names and links. A rerun or a later source can delete that
  place, and the names other sources attached to it go with it. Event catalogues follow the same
  "creator owns the fields" rule. Media take their point from the first target they were stored for.
  Identity columns are random (`uuidv7()`), and conflict resolution compares wall-clock timestamps.
  Two databases that load the same pinned extracts in a different order end up with different place
  ids. The bundle's reference fingerprint is an md5 of those ids, so it differs too, and the bundle
  does not carry the data that would let the two databases converge.
- **The fix is to store what each source said and compute everything else.** Each source record is
  stored once, as received: an *observation*. Person and agent edits are observations too, with a
  hybrid logical clock. Combining two sets of observations is a join-semilattice: per key, the greater
  element under a total order. Place identity, every displayed field, finding-to-place matches and
  the cluster ids are a pure function of (observations, rules version, rules parameters). The current
  tables become a cache of that function.
- The model the main session proposed is sound. It needs these refinements, each explained in §3:
  - Deletion upstream is release-level liveness, not a per-record "newest wins".
  - Configuration that affects the result belongs to the rules version.
  - Fuzzy matching uses tiered edges with cannot-link constraints and a diameter cap, evaluated per
    candidate component in a canonical order. Plain connected components would chain.
  - References store a stable member key, never a cluster id.
  - Person edits carry what they were based on, so concurrent edits are detected and not silently
    lost.
  - Floating-point, collation and Unicode behaviour are pinned.
  - Reference-row uids are derived (UUIDv5), not random.
- A large part of the observation store already exists. `place_names`, `place_links` and
  `place_descriptions` carry `(import_source, import_ref)`, which is one row set per source record
  (V071). `event_catalogue` is one row per catalogue record (V085). Today these rows hang off a place
  or event chosen by arrival order. The migration (§5) re-keys them to their record and derives the
  place from the records. Every existing place id stays valid as an alias.

---

## 1. The requirement

The maintainer's requirement: the mechanism that combines places, sites, events and media from many
sources must give a result that depends only on the **set** of data, never on the order in which
sources were fetched or imported. Data is to be exported and imported between databases, and two
databases that exchange their data must reach the same state.

### 1.1 Why a semilattice, and why idempotence

Write the stored data as `S` and the combining operation as `⊔`. The requirement asks for:

| law | statement | what breaks without it |
|---|---|---|
| commutative | `A ⊔ B = B ⊔ A` | loading GeoNames then Wikidata differs from Wikidata then GeoNames (the case today, §2.1) |
| associative | `(A ⊔ B) ⊔ C = A ⊔ (B ⊔ C)` | batching matters: one import of three sources differs from three imports, and a bundle relayed through a third database differs from a direct one |
| idempotent | `A ⊔ A = A` | rerunning a load, importing the same bundle twice, or an exchange that sends back what the other side already had changes the state |

A monoid has the first two laws but not the third. Exchange needs the third. When A sends its data to
B and B later sends its whole state back, A receives its own data a second time. Only idempotence
makes that a no-op. A structure with all three laws is a **join-semilattice**, and it gives
convergence: two replicas that have received the same set of updates, in any order and with any
repetition, are equal. This is the CRDT state-merge argument (Shapiro et al., "Conflict-free
Replicated Data Types", 2011).

The semilattice must hold for **what is stored**, not only for what is displayed. Everything
displayed is then a deterministic function of what is stored:

```
stored   S = observations ∪ edits ∪ tombstones          (a join-semilattice, §3.1)
derived  D = f(S, rules version r, rules parameters p)  (a pure function, §3.7)
```

When `S_A = S_B` and `(r, p)` is the same on both sides, `D_A = D_B` byte for byte, and a digest of
`D` proves it (§3.7).

### 1.2 What does not need to be deterministic

Fetching need not be deterministic. A SPARQL query can time out, and an LLM gives different output on
different runs. What must be deterministic is what happens **after** a result has been stored as an
observation: two databases that store the same observation must derive the same thing from it.

---

## 2. Audit: where today's code depends on order

### 2.1 Gazetteer loads (`histmersion places load geonames|wikidata|tgn|lpf`)

The path is `GazetteerLoad.load` → `GazetteerRepo.Session` (`prune`, then `batch`, then `sweep`).

**How a record is placed.** `GazetteerRepo.write` (`GazetteerRepo.scala:202-275`) looks up every
place that one of the record's ids already names (`:207-212`). It then calls `GazetteerRepo.place`
(`:111-116`), which calls `Gazetteer.canonical` (`GazetteerSources.scala:222-228`). The result is
`Create` (a new place with id `r.placeId`, e.g. `wikidata_q42`, `ExternalId.placeId` at `:77`),
`Own` or `Join`. `canonical` ranks the candidates in this order:

1. "native" first: a seed id, or an id that *the record which created the place* gave it (`:209`);
2. the record's own id before one of its links;
3. source rank: seed, then LPF, Wikidata, TGN, GeoNames (`rank`, `:200-205`);
4. the smaller id.

The scaladoc states the limitation itself: "Deterministic for a given store" (`:196-199`). Rule 1
depends on which record arrived first. Suppose a GeoNames record `gn:G` links Wikidata `Q` and the
Wikidata record `wd:Q` links `gn:G`:

- GeoNames loaded first creates `geonames_G`. Wikidata's `wd:Q` then joins it, because `gn:G` is
  native there.
- Wikidata loaded first creates `wikidata_q`. `gn:G` then joins it.

Same set, two different place ids. The test `a link a joining record copied never pulls another
record away on the next run (Varaždin)` (`GazetteerRepoSuite.scala:167-181`) pins this down. Its
comment says the outcome depends on "Y, read BEFORE X", and it asserts stability under a **rerun**,
not under reordering.

**Who owns the fields.** Only `Create`/`Own` records write the `places` row (`:266`, `writePlaces`
`:281-315`): its label, kind, geometry, period and `start_unknown`. A `Join` record contributes
names, links and descriptions only. Its class, point and dates survive only on its own link row
(`item_class`, `item_geom` points only, `item_period`, V077, `:374-423`). A polygon a joining record
carries is dropped. So the label, kind, shape and dates of a place are those of **whichever source
created it first**. For a `place_links` row, "a link another record wrote first stays its" (`:370`,
the `ON CONFLICT … WHERE` at `:409-421`): first writer wins.

**Live evidence (read-only, 2026-10-28).** Places by creating source:

| creator | places |
|---|---|
| wikidata | 1 056 272 |
| geonames | 664 717 |
| seed | 60 666 |
| tgn | 37 087 |
| local | 2 |

Places whose names come from another source than the creator, by (creator, contributor):

| creator | contributor | places |
|---|---|---|
| wikidata | geonames | 469 023 |
| geonames | wikidata | 2 657 |
| tgn | wikidata | 39 |

Both directions occur, because different runs met different stores. Ids naming more than one place:
4 781 Wikidata ids, 3 074 GeoNames, 340 Pleiades and 3 TGN. Some of these are split clusters, some
are legitimate close matches, and nothing tells the two apart today. `gazetteer_sources` records
TGN, then Wikidata, then GeoNames as the latest import order (all 2026-10-27). A different order on
another database would give different ids for the same ~470 000 joined places.

**Deletion when the placement moves.** When a record's ids now name a better place, its old own
place is deleted: `stale`, collected at `:116` and deleted at `:255-258`, `DELETE FROM places WHERE
id = ANY(removed) AND import_source = $source`. The point-area path checks for evidence first
(`doomed`, `:231-238`, attestations and asset links). The stale path checks nothing.
Because of the foreign keys:

- `place_names`, `place_links`, `place_relations`, `finding_places`, `place_overrides` and
  `place_aliases` rows cascade away (V005, V016, V055, V080), including the names **other sources**
  attached to that place.
- `events.place_id` is set to NULL (V017:33).
- `attestations`, `asset_links`, `media_links` and `media_checks` name the place without a key, so
  they dangle.
- No `place_aliases` row is written, so the old id stops resolving.

Live: 1 media link and 5 media checks name a place that no longer exists. The "17 526 joined an
existing place … 8 226 place(s) removed" line comes from `GazetteerLoadCounts.describe`
(`GazetteerSources.scala:536-541`). It sums these stale deletions, the point-area prune and the sweep.

**Prune and sweep.**
- `Session.prune` (`GazetteerRepo.scala:163-178` → `PointAreaRepo.deletePlacesIn`,
  `PointAreaRepo.scala:137-149`) deletes this source's point-only region, polity and people places
  before the first batch. With them go the names any other source joined to them. The test `a place
  an earlier import made for a point-only area goes on the next run, with what joined it`
  (`GazetteerRepoSuite.scala:243-259`) confirms this. After it, the GeoNames record that had joined
  has no rows at all (`importRefs("geonames:…") = 0`) until GeoNames is loaded again. So "Wikidata,
  GeoNames, Wikidata" leaves a different state from "Wikidata, Wikidata, GeoNames".
- `Session.sweep` (`:182-200`) deletes the places of records the new version no longer has, with the
  same cascade.

**Configuration in the result.** `places.sources.open-start-window-years` (`Gazetteer.validity`,
`:246-249`) and `places.sources.point-area-kinds` (`dropsPointArea`, `:281-282`) are configuration.
`places.point-tolerance-km` drives `rebuildFindingPlaces`. Two databases with the same data and
different settings store different periods, a different set of places and different finding matches.

**Within one file.** A ref repeated in a batch keeps its first occurrence (`:139-140`). The extracts
are sorted by id (`db/gazetteer/build.py:24`, `dump_line` with `sort_keys`), so this is
deterministic for a given file. It is still a first-wins rule.

### 2.2 The bundled seed (`places seed`, `init`, AWMC, Cliopatria)

`PlaceRepo.upsertIn` (`PlaceRepo.scala:150-196`) writes seed places under their own ids (`pleiades_…`,
`ne_…`) with `ON CONFLICT (id) DO UPDATE` (`:162`): the last writer wins on the row. It replaces the
seed's own names (`:172`) and writes the seed's cross-ids with `linkSeedIds`
(`GazetteerRepo.scala:40-60`). Seed ids do not depend on order. The interaction with the external
sources does:

- seed first: Wikidata and GeoNames records join the seed place (rank 0);
- seed after Wikidata: the new seed place and `wikidata_q…` exist side by side until the next
  Wikidata load moves the record (§2.1, stale deletion, no alias).

`suppressed` (`:142-147`) keeps a removed or merged-away id from being recreated. That is a
tombstone, but it applies only to seed ids.

### 2.3 Finding-to-place matches

Findings have no place column. `finding_places` is derived by the geocoder
(`PlaceRepo.rebuildFindingPlaces`, `Geocoder.candidates`, `Geocoder.scala:32-55`). The ranking uses
`tierSql` (`:57-60`), which ranks by `p.import_source`, i.e. by **who created** the place. That
inherits the order dependence of §2.1, and the final tie-break on `p.id` (a creator-dependent id)
does too. Because the match is derived, findings themselves survive any re-clustering. Only the
inputs to the geocoder need fixing.

### 2.4 Event catalogues (`events import gvp|noaa|wikidata`)

`CatalogMerge.place` (`EventCatalogue.scala:183-216`) is deterministic **within one run**: records
are sorted by key and candidates ranked by `Fit` with an id tie-break (`:154-156`). Across runs it
favours what is already stored. Rules 1 to 3 look at stored events first (`:191-197`), and rule 4
looks only at records placed earlier in the run. The consequences:

- **Event id**: the id of the record that created the event (`CatalogRecord.eventId`,
  `EventCatalog.scala:69-72`). Load GVP first and a NOAA eruption joins `gvp_…`. Load NOAA first and
  the event is `noaa_volcano_…`, which the GVP record then joins.
- **Fields**: `save` (`EventCatalogRepo.scala:88-121`) refreshes an event only from its OWNER, a
  record of the catalogue that created it (`CatalogRecord.owner`, `EventCatalog.scala:76-78`).
  Records of other catalogues only `fill` (`:161-166`). The Wikidata item uses `coalesce(wikidata,
  $wd)`, so the first one wins. Names are a set union, which is order-independent.
- **Greedy grouping**: rule 3 skips a stored event that already holds a record of the same catalogue
  (`EventCatalogue.scala:195`). Whichever record reached a candidate first takes it, and a second
  record of that catalogue makes a new event.
- **Clocks**: `retrieved_at = now()` on every upsert (`EventCatalogRepo.scala:210`), and
  `event_import_parts.done_at` (`:220`). In a bundle, "the newer `retrievedAt` wins" (§2.6).

### 2.5 Media providers

`MediaRepo.save` (`MediaRepo.scala:351-371`):

- An `assets` row is keyed by `(provider, provider_key)`, which is stable. Its `uid` is `uuidv7()`
  (V030, V082:31), random per database.
- A refetch replaces bytes and rights, so the last fetch wins. `retrieved_at = now()`.
- The point and period come from **the first target the picture was stored for** (`placeAt`,
  `:376-391`, `coalesce(a.geom, …)`). The same picture reached through another place first gets
  another point.
- `media_links.target_id` names a place or event id without a key (`:400-409`), and so does
  `media_checks` (`:419-428`, `clock_timestamp()`). They dangle when the gazetteer deletes a place
  (§2.1).
- `commonsCategory` (`:411-415`) writes a `place_links` row with `ON CONFLICT DO NOTHING`: first
  wins.
- Provider pictures do not travel in a bundle. `BundleRepo.assets` joins `sources`
  (`BundleRepo.scala:294-300`), and provider rows have no citekey.

### 2.6 Bundle export and import, "merge another collection"

- **Gazetteer and seed rows do not travel.** Only `dataset = 'local'` places do
  (`BundleRepo.scala:462-463`). All imported places are written `dataset = 'seed'`
  (`GazetteerRepo.scala:299`). The manifest carries a *reference fingerprint*: count and md5 of the
  seed place ids (`BundleRepo.reference`, `:550-558`). A mismatch is only a note ("events point into
  them by id", `MergePlanner.scala:1523-1525`). Because the ids depend on order (§2.1), two databases
  with the same extracts can disagree on the fingerprint, and a bundle event's `placeId` can name a
  place the receiver holds under another id.
- **What does travel is derived rows**, not observations: places with their current names, links and
  types (`BundleImport.placesAndPeriods`, `BundleImport.scala:457-500`). On import these are replaced
  wholesale (`DELETE … INSERT`) or added with `ON CONFLICT DO NOTHING`.
- **Matching is fuzzy and asymmetric.** `MergeState.matchPlaces` (`MergeState.scala:130-150`) matches
  on reduced label, kind and distance or overlap, and prefers `dataset = 'local'`, i.e. *this* side.
  A difference becomes `place-differs` with `keep-left | keep-right`. Merge A into B with `keep-left`,
  then B into A with `keep-left`, and each side keeps its own value. The two do not converge.
- **Timestamp rules.** `BundleEventSets.newer` (`Bundle.scala:360-363`) compares timestamps as
  strings with a strict `>`. On a tie, the local side wins on both sides, so equal-timestamp,
  different-content rows diverge. Catalogue rows (`retrievedAt`), checks, corrections (`updatedAt`)
  and site types (`checkedAt`) all use it (`BundleImport.scala:700-788`). The apply also sets
  `updated_at = now()` on folded rows (`:195-198`). This advances the receiver's clock, so its rows
  look "newer" on the way back.
- **Not carried at all**: `place_overrides`, names added by hand on seed places, provider media,
  media links, and aliases of seed places (`placeAliases` filters `dataset = 'local'`, `:526-533`).
- **Tombstones** exist for uid-bearing rows (V031) and for local places only (V052:73-74). They carry
  `deleted_at = now()`. The untombstone trigger deletes a tombstone when the same uid is inserted
  again (V031). That makes a re-add win over a delete by arrival order, not by any clock.

### 2.7 Person corrections and overrides

- **`place_overrides`** (V080, V081, `PlaceOverrideRepo.set`, `PlaceOverrideRepo.scala:40-127`). One
  row per place id. A trigger forces the override over any writer of `places`. The row snapshots
  what the source said (`source_kind`, `source_period`, `source_geom`). Successive edits merge with
  `coalesce` in arrival order and `updated_at = now()`. There is no clock, no node and no history.
  The row cascades away with the place (`ON DELETE CASCADE`). The stale deletion of §2.1 does not
  check for overrides, so the gazetteer can remove a person's decision. It does not travel in a
  bundle. Live: 17 rows.
- **`corrections`** (V088, `CorrectionRepo`). One row per (target, field), `bigserial` id,
  `updated_at = now()`, last write by arrival order (`record`, `:38-51`). The status
  (`active | agreed | conflict`) is **stored** and moved by each refresh (`reconcile`, `:53-66`), so
  it depends on the sequence of refreshes and not only on the values. A bundle carries corrections
  with "newer `updatedAt` wins". Live: 0 rows.
- **Manual names** (`place_names.manual`, V068). Add and remove by arrival order. Not carried in a
  bundle for seed places.
- **Place merge** (`PlaceReviewRepo.mergeIn`, `PlaceReviewRepo.scala:101-125`). Destructive: the
  merged place's own names and parents are deleted with it (`:114`), its links are copied with
  `ON CONFLICT DO NOTHING`, and an alias is written. A rename (`PlaceRepo.renameIn`, `:1243-1256`)
  is similar. Neither is an observation that could be replayed on another database.

### 2.8 Hidden sources of divergence

| what | where | effect |
|---|---|---|
| `uid uuidv7()`, `origin = instance` on every place, event and asset row | V030, V052 | the same Wikidata place or GVP eruption has a different uid on each database |
| `updated_at` / `retrieved_at` / `checked_at` = `now()` as conflict tie-breaks | V030 trigger, §2.4–2.7 | the wall clock decides content |
| "first seen" | `canonical` native-first, place creator, event owner, media first target, `coalesce` fills, `ON CONFLICT DO NOTHING` links | arrival order decides content |
| configuration | §2.1 | settings decide content |
| PostGIS geography distance and `ST_Area` ratios in matching | `MergeState.scala:140-142`, `CatalogMerge.near` (JVM, `Geocode.distanceKm`) | results can differ across PostGIS/PROJ versions and around the tolerance edge |
| text normalisation | `histmersion_place_key` (V067), `normLabelSql` | deterministic SQL, but it is a hidden part of the rules: a change to it changes matches without any version bump |
| collation | most `ORDER BY` use the database collation; the bundle uses `COLLATE "C"` | tie-breaks on text can differ between databases with different locales |

### 2.9 Audit table

| component | current behaviour | order-dependent? | what changes |
|---|---|---|---|
| `Gazetteer.canonical` / `GazetteerRepo.place` | join the best place an id already names; native-first, then rank, then id (`GazetteerSources.scala:222`, `GazetteerRepo.scala:111`) | **yes**: native depends on who came first | identity becomes clusters over all observations (§3.3) |
| place id | the id of the creating record (`ExternalId.placeId`) | **yes** | cluster id from members by a fixed rule; every member key an alias (§3.3.5) |
| place label, kind, geometry, period | the creating record's (`writePlaces`) | **yes** | per-field rules over the whole cluster (§3.4) |
| names, descriptions | union per record, keyed `(import_source, import_ref)` | the union is not; their **place** is | keep the rows, re-keyed to the record (§5 step 2) |
| links | first writer keeps the row (`:370`, `:409-421`) | **yes** | per-record link observations; all kept |
| stale own place | deleted, no pin check, no alias (`:255-258`) | **yes** | nothing is deleted; membership moves |
| point-only areas | deleted with what joined them (`prune`, `PointAreaRepo.deletePlacesIn`) | **yes** | a derived visibility rule; observations stay (§3.4) |
| sweep | deletes unseen records' rows and places, cascading others' names | **yes** | release liveness (§3.2); a cluster loses one member, not its other members' rows |
| open-start window, point-area kinds, tolerance | configuration | **yes** (by settings) | part of the rules profile (§3.7) |
| seed upsert | own ids, last writer per id (`PlaceRepo.scala:162`) | partly (between seed files sharing an id) | seed files are observation datasets like the others |
| geocoder tier | creator's `import_source` (`Geocoder.scala:57`) | **yes** | the cluster's best member rank; stable tie-break key |
| `CatalogMerge.place` | stored events first, then greedy in key order | **yes** across runs | event clusters (§3.9) |
| event id and fields | the creator's catalogue owns them; others fill; `coalesce` Wikidata | **yes** | per-field rules over the cluster |
| `event_catalogue.retrieved_at` | `now()`, newer wins in a bundle | **yes** (clock) | release version plus content hash |
| corrections | per field, arrival order; stored status | **yes** | HLC edits; status derived (§3.5) |
| `place_overrides` | one row, `coalesce` arrival order, cascades with the place | **yes** | HLC edits per field, keyed by member key |
| manual names | add and remove by arrival | **yes** | observed-remove set (§3.5.2) |
| place merge and rename | destructive | **yes** | same-as / not-same-as observations (§3.5.3) |
| media asset | uid random; point from the first target; last fetch wins | **yes** | uid = UUIDv5(provider, key); point derived; version plus hash |
| media links and checks | text target id, dangle on deletion | indirectly | target = member key |
| bundle: places | local only; derived rows; fuzzy match; keep-left/right | **yes**, and A into B ≠ B into A | observations plus edits travel; no decisions for reference data (§3.10) |
| bundle: catalogue, corrections, site types | newer string timestamp; tie means local wins | **yes** | total order (version or HLC, node, hash) |
| bundle: reference fingerprint | md5 of seed place ids | **yes** (ids depend on order) | digest of observations plus rules, and of derived state |
| tombstones | `now()`; any reinsert untombstones | **yes** | remove observations in the same order as adds |
| uids of reference rows | `uuidv7()` | **yes** | UUIDv5 of the stable key |

---

## 3. The model, refined

### 3.1 Observations: the stored state

There are three kinds of observation. Each is a row whose key and order make the store a
join-semilattice.

**(a) Source records.** One row per record of a source, as received:

```
ref_records(
  dataset      text,      -- 'wikidata', 'geonames', 'tgn', 'seed:pleiades', 'seed:ne', 'lpf:<slug>',
                          --  'osm', 'register:<plugin>', 'catalogue:gvp', 'media:<provider>' …
  record_key   text,      -- the record's id in its source: 'Q42', '2643743', 'node/123', 'gvp:13879'
  scope        text,      -- the release scope it was read in ('' = the whole dataset; a band, a part, a target)
  version      text,      -- the release's version (sortable, §3.2)
  content_sha  bytea,     -- sha256 of the canonical JSON of the parsed record
  parsed       jsonb,     -- label, kind, geometry (WKB hex), span, item class, properties
  PRIMARY KEY (dataset, record_key))
ref_record_names(dataset, record_key, name, lang, span, citation)      -- what place_names holds today
ref_record_links(dataset, record_key, scheme, external_id, relation)   -- what place_links holds today
ref_record_descriptions(dataset, record_key, lang, text)
```

The order on `(dataset, record_key)` is `(version, content_sha)` lexicographic. The store keeps the
maximum per key, so the join is a pointwise maximum: commutative, associative and idempotent. An
older or equal observation is a no-op. The raw payload of a pinned extract is not stored per row:
the extract file is the payload, addressed by its sha256 as today (`db/gazetteer/extracts.json`).
For datasets fetched from a live API (Wikidata events, media providers, registers without dumps),
`parsed` keeps everything the rules read, and `raw` jsonb may be kept as well.

**(b) Releases.** One row per (dataset, scope, version):

```
ref_releases(dataset, scope, version, sha256, records int, complete boolean,
             licence, citation, fetched_from, PRIMARY KEY (dataset, scope, version))
```

A set, so the join is set union.

**(c) Edits by people and agents** (§3.5, §3.6), with a hybrid logical clock:

```
ref_edits(target_key, field, value jsonb, based_on jsonb, hlc bigint, node uuid, actor text,
          PRIMARY KEY (target_key, field, hlc, node))
```

Plus the derived "current" view per `(target_key, field)` = the maximum by `(hlc, node)`. Names added
by hand, same-as links and suppressions have their own set shapes (§3.5).

Everything else (`places`, `place_names`, `events`, `finding_places`, the map tiles) is derived.

### 3.2 Releases and deletion upstream

The main session proposed "newer versions of the same record win". That covers changed records. It
does not cover records the source **dropped**. Absence is not an observation, and a union cannot
delete. Today the sweep expresses deletion destructively. The replacement is **release liveness**:

> A record observation at version `v` in `(dataset, scope)` is **live** iff no `complete` release of
> that `(dataset, scope)` with a version greater than `v` exists.

This is a function of the two sets, so it keeps the semilattice. An example:

- Database A has `wikidata@2026-10-27` (complete), without `Q7`.
- Database B has `Q7@2026-08-10` from the older release.
- After the exchange, both hold both. `Q7` is dead on both, because a later complete release exists
  and does not list it.
- If `Q7` reappears in a later release, it is observed at that version and is live again.

No per-record tombstone is needed, and the result does not depend on whether A ever held `Q7`.

Scopes cover partial reads. The Wikidata extract's bands, an event catalogue's `parts`, and a media
provider's per-target query are each a scope. Dataset-level releases mark `scope = ''`.

**Version order.** A version must sort. The pinned extracts already use ISO dates
(`extracts.json`: `2026-10-27`). LPF files use the sha prefix today, which has no order. Their
version becomes `(file mtime date or stated date, sha)`. Live API reads use the UTC retrieval
instant. When two releases share a version string, the release sha breaks the tie. The rule always
gives an answer, but it can pick an arbitrary "winner" (§7, Q10).

### 3.3 Identity: clusters computed from the set

#### 3.3.1 Members and edges

Every live source record is a **member**. Its **member key** is `dataset:record_key`. For the
existing kinds it is spelled as today's place id, so existing references already name a member:
`wikidata_q42`, `geonames_2643743`, `pleiades_579885`, `tgn_7011944`, `ne_country_tur`,
`lpf_<dataset>_<tail>` (`Gazetteer.placeIdFor`, `GazetteerSources.scala:359-363`). A person's own
place is a member of dataset `local`.

Edges come in tiers. Each edge is undirected and computed by a pure function of the two members:

| tier | edge | from |
|---|---|---|
| T0 | person **same-as** | `ref_edits` (§3.5.3) |
| T1 | **strong id**: member A's own id is listed as an exact link by B, or both list the same external id as exact | Wikidata P1584 (Pleiades), P1566 (GeoNames), P1667 (TGN); GeoNames' Wikidata links; Pleiades' and NE's cross-ids (`linkSeedIds`); LPF `exactMatch`; OSM `wikidata=`; a register's id carried by a Wikidata property or an OSM `ref:*` tag the plugin declares |
| T2 | **close link**: LPF `closeMatch`, the `close_match` rows today | as above |
| T3 | **fuzzy**: same name key, compatible kind, within distance `d(kind)` | the deterministic predicate of §3.3.3 |

And constraints (cannot-link):

| constraint | meaning |
|---|---|
| C1 | person **not-same-as** (§3.5.3) |
| C2 | two different **own** ids of the same authority (two Wikidata items, two Pleiades places) may not share a cluster through T2/T3 edges, only through T0 or T1 |
| C3 | the cluster's diameter (maximum pairwise distance of members' representative points) stays within `D(kind)` for T3 edges |
| C4 | events only: two records of the same catalogue are never one event, unless the catalogue itself links them (today's rule 3, `EventCatalogue.scala:195`) |

#### 3.3.2 Why plain connected components fail, and the evaluation order

Connected components over every edge are order-independent but **chain**. One wrong Wikidata link
or a run of same-named shrines a kilometre apart would fuse unrelated places, and OSM will add
millions of points with common names ("War Memorial", "St Mary's Church"). The fix is a constrained
union, evaluated in a **canonical order** that is itself a function of the set:

1. Build the **candidate graph** from every edge of every tier, and take its connected components.
   These are the units of recomputation. They are order-independent, being plain components.
2. Inside each candidate component, sort the edges by `(tier, strength, min key, max key)`, where
   strength for T3 is the integer distance. Apply them one by one with union-find. Accept an edge
   unless the resulting cluster would violate C1 to C4.
3. The accepted unions are the clusters.

Step 2 is a deterministic function of the component's edge set: the order is computed from the set,
not the arrival order. It is Kruskal's algorithm with constraints. Adding or removing any edge
changes only the candidate component it touches. That component is recomputed **from scratch**, so
the incremental result equals the full result by construction. Rejected edges are kept in a derived
table with their reason (`conflict: two Wikidata items`, `too wide`), and lint shows them for review.

A candidate component larger than a set bound (say 2 000 members) is still computed, but flagged.
That is a signal that a strong id is wrong (a country item linked from thousands of records).

#### 3.3.3 Keeping the fuzzy predicate deterministic

T3 is the only judgement in identity. It must be a fixed function:

- **Name key**: one normalisation, written once in Scala (NFKD, strip marks, case-fold with
  `Locale.ROOT`, collapse punctuation). Pin the JDK's Unicode version in the rules version. The SQL
  `histmersion_place_key` (V067) becomes a mirror checked against the Scala function by a test, not a
  second definition.
- **Distance**: computed in the JVM on integer microdegrees, with `StrictMath` haversine on a fixed
  radius. Not PostGIS `geography`, whose results depend on the PostGIS and PROJ versions. Thresholds
  are integer metres.
- **Kind compatibility**: a fixed table (site ~ feature; settlement ~ site; region never ~ site).
- **Blocking**: pairs are generated only within (name key, fixed grid cell) and the eight
  neighbouring cells, with the cell size ≥ the largest `d(kind)`. Blocking changes cost, never the
  answer, because the predicate is evaluated exactly on every pair it could accept.
- **Which records may take T3 edges**: by default only records with **no** strong id (OSM POIs
  without `wikidata=`, register entries without a cross-id) may attach by name and distance.
  Records that both carry strong ids join only through T0, T1 or T2. This alone prevents most
  chaining.

#### 3.3.4 The cluster id

The main session proposed "min member key". That is stable under arrival order but changes whenever
a smaller key joins. Changes are unavoidable when membership changes, but they should be rare and
predictable. The rule:

> cluster id = the member key that is minimal under (authority rank, key), with the rank: local
> (a person's) < seed (Pleiades, NE, curated) < LPF < Wikidata < TGN < GeoNames < OSM < registers.

This is today's `rank` order (`GazetteerSources.scala:200-205`) without "native first". Most of the
1 056 272 Wikidata-created places keep their `wikidata_q…` id, and most GeoNames-created places with
a Wikidata member move to `wikidata_q…`. §5 step 3 measures how many change before anything is
switched.

#### 3.3.5 References never store a cluster id

Every reference into the gazetteer stores a **member key**: `attestations.target_id`,
`asset_links.target_id`, `media_links.target_id`, `media_checks.target_id`, `events.place_id`,
corrections, overrides and `site_type`. Because today's ids are member keys, existing rows need no
rewrite.

A derived table `place_keys(key → cluster_id)` holds every member key and every legacy alias
(`place_aliases`, and the ids that merges and renames left). Reads resolve through it, as
`GET /api/places/{id}` already does through `place_aliases` (V055). When clusters merge, both members'
references land on the merged cluster. When a cluster splits, each reference follows its own member.
Either way the outcome is well defined, and it is the same on every database.

Aliases that name no member (a merged-away id with no record behind it) are kept as observations of
kind `alias(key → member key)` with HLC, so they travel and resolve the same everywhere.

### 3.4 Field resolution: every displayed field, per cluster

Each derived field is a pure function of the cluster's live members and the edits that apply to
them. The rules below are the proposal. They are versioned (§3.7), and the maintainer should review
the priority lists (§7, Q1).

| field | rule |
|---|---|
| names | union of members' names, each with its member(s) as provenance; `(name, lang)` collapsed with `Gazetteer.mergeNames` (already order-independent: widest span, citations sorted, `GazetteerSources.scala:294-308`); names added by hand (§3.5.2) added; suppressed names removed |
| label | person edit, else the name of the top member by (authority rank, key); the UI language may pick another name **at read time** |
| kind | person edit, else the first member in a per-field priority (seed > LPF > TGN > Wikidata class mapping > GeoNames feature code > OSM tag) whose kind is set; key breaks ties |
| geometry | person point (only over a point, as V081 does), else the most precise: a polygon or line from the highest-ranked member that has one, else a point by precision (`location_precision` not `grid-cell` before `grid-cell`), then rank, then key |
| period / `start_unknown` | person edit, else the highest-ranked member that states one. Not the hull, which would overstate. All per-member periods are kept in a derived table so the card can say "sources disagree" |
| site type | person edit > agent observation (§3.6) > none |
| place visible as its own place? | the point-area rule becomes **derived**: a cluster whose only area-kind members are point-only bulk-source records is not drawn and not geocoded as a region. Nothing is deleted, and the records' names stay on any cluster they belong to |
| geocoder tier | the rank of the best member, not the creator |
| links | union of members' links, with provenance |
| relations (part-of) | union of member statements, re-pointed to clusters; a cycle created by a merge is broken by dropping the edge with the larger key |

Every rule ends in a total order on member keys, so no rule can tie.

### 3.5 Person edits and tombstones

#### 3.5.1 Field edits: HLC last-writer-wins, plus based-on

An edit is `(target_key, field, value, based_on, hlc, node)`:

- `target_key` is a **member key**, so the edit survives re-clustering.
- `hlc` is a hybrid logical clock (Kulkarni et al., 2014): 48 bits of physical milliseconds and a
  16-bit counter. The instance keeps its last HLC in the `instance` table and advances it past every
  HLC it imports.
- `node` is the instance id (V030). Ties are impossible after `(hlc, node)`.

The current value is the maximum by `(hlc, node)`, which is a join-semilattice per `(target, field)`.

LWW alone cannot tell "B edited after seeing A's edit" from "A and B edited at the same time without
seeing each other". In the second case one person's work disappears silently. `based_on` is the
`(hlc, node)` of the value the editor saw (null for the source's value). Two edits with the same
`based_on` and different values are **concurrent**. Both are kept, the LWW winner is shown, and lint
reports the conflict. This is deterministic and needs no version vectors.

Today's tables map onto this:
- `place_overrides` becomes edits of `kind`, `period` and `point`. The `source_*` snapshot columns
  are no longer needed: the source's value is always the derived value without the edit.
- `corrections` become edits whose `based_on` also records the upstream value at edit time (today's
  `upstream_value`). The status (`agreed`: edit = current upstream; `conflict`: upstream changed since
  `based_on`; `active`) becomes **derived**, not stored, so it no longer depends on the order of
  refreshes.

#### 3.5.2 Sets: names added by hand, suppressions

Names added by hand are an **observed-remove set** (OR-set). Each add has a unique tag `(hlc, node)`.
A remove lists the tags it saw. Visible = adds whose tags no remove lists. Add and remove commute,
and a concurrent re-add survives a remove that did not see it. This replaces the arrival-order
`manual` flag (V068) and the untombstone trigger's "any reinsert wins" (V031).

Suppressing a source record or a place ("remove this place", today's `place_removals`) is an LWW
register `suppressed(member key)` with HLC. A later un-suppress is a later write. A suppressed member
leaves its cluster's display but keeps its observation, and so its references and its links to
others.

#### 3.5.3 Merges and splits by a person

"These are the same place" and "these are different places" are edges, not destructive operations:
`same_as(a, b)` (tier T0) and `not_same_as(a, b)` (constraint C1). Each is an LWW register per
unordered pair. A later write of the opposite kind replaces it. If a T0 edge and a C1 constraint
apply to the same pair, the later one by `(hlc, node)` decides. If they sit on different pairs and
contradict each other transitively, the canonical evaluation order of §3.3.2 decides (T0 edges first,
by key), and lint reports the contradiction.

This replaces `PlaceReviewRepo.mergeIn`, which deletes the merged place's names, and also replaces
`places rename`. A rename becomes an `alias` observation plus a label edit.

### 3.6 Agent output

An agent's answer is an observation: `(target_key, field, value, producer, hlc, node)`, where
`producer` is the model id plus the prompt or pipeline version (for example the site-type classifier
behind `places.site_type`, `EventCatalogRepo.setSiteTypes`, `:427-430`). Producing it is not
deterministic. Once stored, it merges like any edit:

- An agent value never beats a person's edit of the same field (actor class order: person > agent >
  source).
- Between agent values, LWW by `(hlc, node)`.
- Rerunning the agent adds an observation and never rewrites one.

### 3.7 Rules versions, parameters, recompute and digests

**The rules profile.** `derived = f(S, r, p)`:

- `r` is the code version of the rule set: clustering, field rules, name key, kind table, geocoder.
  It is an integer bumped whenever any of them changes output.
- `p` is every parameter that changes output: the open-start window, the point-area kinds, the
  distance thresholds, the tolerance for finding matches, and the authority ranks.

`(r, p)` is stored in the database (`ref_rules(version, params jsonb, sha256)`), not read from
`reference.conf` at run time, and its sha goes into every digest. A database may change its
parameters. Its derived state then legitimately differs, and the digest says so before anyone
compares maps.

**Incremental recompute.** An import, an edit or an exchange changes a set of members `M`.
Recompute:

1. Find the candidate components touching `M`, both before and after the change (their union).
2. Recompute the clusters, then the fields, then `place_keys`, then the finding matches of the
   affected clusters (`PlaceRepo.materialisePlaces` already works per place).
3. Write the derived rows in a transaction per batch of components, with set-based statements.

A re-import of an unchanged release touches no member, because every `content_sha` is equal.

**Full recompute as verification.** `lint --rule ref-determinism` (or a `places verify` job)
recomputes everything into shadow tables, compares their digests with the live ones, and reports any
difference. Any difference is a bug in the incremental path, and the full result is what should be
in place.

**Digests.**
- The observation digest is the sha256 over the canonical, sorted serialisation of `ref_records`
  (key, version, sha), `ref_releases`, `ref_edits` and the sets.
- The derived digest is the sha256 over the sorted derived rows: clusters, `place_keys` and resolved
  fields.

Both replace the md5 of seed ids in the bundle manifest (`BundleReference`). Sorting uses `COLLATE
"C"` or the JVM, never the database's locale collation.

### 3.8 Findings, citekeys, slugs and aliases across cluster changes

- **Findings** have no place column. `finding_places` is derived (§2.3). After a cluster change, the
  findings of the affected clusters are matched again. Nothing a person wrote in a finding
  (`region_name`, `lat`/`lon`, `period`) changes.
- **Citekeys and article slugs** do not take part. An article's `[[citekey]]` and `[[slug]]` links
  are untouched, and so are source aliases.
- **Place references in sources** (attestations, asset links), **events** (`place_id`) and **media**
  store member keys and resolve through `place_keys` (§3.3.5).
- **Existing aliases** (`place_aliases`, V055; 23 rows live) become alias observations and keep
  resolving. A cluster id that changes leaves the old id resolving, because the old id is a member
  key or an alias.

### 3.9 Events and media under the same machinery

**Events.**
- Members are catalogue records (`event_catalogue` rows, already keyed `(provider, provider_id)`)
  and compiled or person-made events (members of dataset `local`).
- T1 edges: the same Wikidata item, or a catalogue's own cross-reference (`CatalogRecord.group`,
  `EventCatalog.scala:31`). T3: same canonical kind, outer spans overlapping within `years`, points
  within `km` (today's `CatalogMerge` predicates, with the JVM distance pinned as in §3.3.3). C4 is
  today's same-catalogue exclusion.
- The event id follows §3.3.4: compiled or person event first, then catalogue rank, then key.
- Fields are chosen per field with a catalogue priority (for example GVP for an eruption's dating,
  NOAA for deaths, Wikidata for the label), not "the creator's catalogue owns everything".
- Corrections are edits (§3.5.1). `event_link_candidates` keeps the existing deterministic rule (a
  decision over a proposal), with the clock replaced by `(hlc, node)`.

**Media.**
- `assets.uid` for a provider picture = UUIDv5(namespace, `provider:provider_key`), the same on
  every database. The bytes are content-addressed (`sha256`).
- The point and period become **derived** from the linked targets: the target with the smallest
  member key, by the same rule as `placeAt`. They no longer depend on which target was asked first.
- `media_links` rows are observations keyed `(provider, key, target member key, role)` within the
  scope of that target's query. `media_checks` rows are releases of those scopes.

### 3.10 Exchange

A bundle carries reference data as observations, never as derived rows. The new `reference/`
section of the `.hmx`:

```
reference/rules.json             the rules profile (r, p, sha)
reference/releases.ndjson        every release: dataset, scope, version, sha256, complete, licence, citation
reference/records/<dataset>.ndjson.gz
                                 the records of datasets the receiver cannot fetch itself (API reads,
                                 plugins, a person's LPF files); pinned extracts travel BY REFERENCE
                                 (their sha256), or with --with-extracts as blobs
reference/edits.ndjson           field edits, OR-set adds/removes, same-as/not-same-as, suppressions, aliases
reference/digest.json            observation digest and derived digest
```

Lines are sorted by key in `C` order, with fixed tar headers as in today's bundle (§4 of
`research-merge-import.md`), so two exports of the same state are byte-identical.

**Import:**
1. Union each set: per key, keep the maximum. Older and equal lines are no-ops.
2. A release referenced by sha that the receiver lacks is queued as "fetch extract `<sha>`". Its
   records stay absent until it is present. Liveness (§3.2) is computed only from releases that are
   present, so a missing extract never kills records.
3. Recompute the affected components (§3.7).
4. Compare digests and report.

No `keep-left` or `keep-right` decision exists for reference data. The only things a person reviews
are the concurrent edits of §3.5.1 and the rejected edges of §3.3.2. Both show the same on either
side, because both are derived.

`--since` remains a transport optimisation: send lines whose HLC or version is newer than what the
peer acknowledged. It is never a conflict rule.

**Convergence argument.** Let `S_A` and `S_B` be the two stores. After A imports B's bundle,
`S'_A = S_A ⊔ S_B`. After B imports A's, `S'_B = S_B ⊔ S_A`. By commutativity they are equal.
Repeating either exchange adds nothing, by idempotence. A third database relaying either bundle
reaches the same state, by associativity. Each derived state is `f(S', r, p)`, so the derived states
are equal when the rules profiles are equal.

---

## 4. Where the main session's model holds, and where this document departs from it

| proposed | verdict | refinement |
|---|---|---|
| join-semilattice, not just a monoid | holds | idempotence is what makes exchange back and forth and reruns safe (§1.1) |
| raw observations, one per record; union; newest version wins | holds for changed records | add **releases** and liveness so that records dropped upstream disappear without destructive deletes (§3.2) |
| identity from pairwise links → connected components | holds for strong ids only | tiered edges with cannot-link constraints, a diameter cap and a canonical evaluation order inside candidate components; T3 only for records without strong ids (§3.3) |
| cluster id = min member key, members as aliases | holds | rank-then-key to limit churn, and references store **member keys**, never cluster ids (§3.3.4–3.3.5) |
| fields by versioned rules over the cluster | holds | the **parameters** belong to the version (§3.7); the point-area rule becomes visibility, not deletion |
| person edits LWW with HLC + node; tombstones | holds | add `based_on` to detect concurrent edits; OR-set for hand-added names; merges and splits as same-as and not-same-as edges (§3.5) |
| agent output merges deterministically once stored | holds | person beats agent; the producer is recorded (§3.6) |
| exchange observations + tombstones, never derived rows | holds | pinned extracts by reference; digests; no keep-left/right for reference data (§3.10) |
| property, permutation and round-trip tests | holds | add incremental = full, the migration check on a restored live copy, and a cross-PostGIS-version check (§6) |
| *(not in the proposal)* | — | hidden non-determinism: floating-point geography, collation, Unicode normalisation, random uids (§2.8, §3.3.3) |

---

## 5. Migration path

Each step can be deployed on its own, keeps the server openable, and loses no data. No existing id
stops resolving.

**Step 0: measure the divergence that exists.** Add the permutation test of §6.2 against today's
code, expected to fail, and record the differences as a baseline. Add `lint --rule ref-split` to
list the 8 198 external ids that name more than one place, so the cluster step can be checked
against them.

**Step 1: keep every record's own fields (dual write).**
- Migration: create `ref_records` and `ref_releases`.
- The gazetteer import writes one `ref_records` row per record, joined ones included. That keeps the
  label, kind, full geometry and period that a joining record loses today.
- Every load writes a release row.
- Backfill from `places` (creator records) and from `place_links.item_*` (joined records' class,
  point and dates). Joined records' polygons are not recoverable from the database. They come back
  when each pinned extract is loaded again. The extracts are on disk and pinned by sha.
- No read changes.

**Step 2: re-key the existing per-record rows.**
- Add `(dataset, record_key)` as the owner of `place_names`, `place_links` and `place_descriptions`.
  They already carry `import_source` and `import_ref`. Seed rows (`import_source = ''`) get
  `dataset = 'seed:<file>'` and `record_key = place id`.
- Keep `place_id` as a derived column, filled from the current membership.
- Stop the destructive paths:
  - Stale own places are no longer deleted; membership moves instead.
  - `prune` becomes the visibility flag.
  - The sweep marks records dead by release instead of deleting places.
- From this step on, no import cascades away another source's rows, and none breaks a reference.

**Step 3: shadow clustering.**
- Implement the clusterer and field rules as pure Scala in `core`: `RefCluster.cluster(members,
  edges, constraints, rules)`.
- A job computes `place_clusters_next`, `place_keys_next` and the resolved fields into shadow tables
  and reports the diff against the live `places`: ids that would change, places that would merge or
  split, fields that would change, findings that would move.
- Nothing switches. The maintainer reviews the numbers and the rejected edges.

**Step 4: switch the place reads to the derived tables.**
- `places` becomes the derived read model (same columns, so the map, tiles and search keep their
  indexes and stay real-time).
- Every live id that is not a cluster id is written into `place_keys` as an alias. References are
  untouched: they are member keys (§3.3.5).
- `finding_places` is re-materialised once. The geocoder reads the cluster's best-member tier.
- The rules profile is written. The full-recompute lint is on.

**Step 5: person edits as observations.**
- Add the `instance.hlc` column and `ref_edits`.
- Backfill:
  - `place_overrides` (17) become edits with HLC from `updated_at` and the local node.
  - Hand-added names (0 live) become OR-set adds.
  - `place_removals` become suppressions.
  - `place_aliases` (23) become alias observations.
  - Past `places merge` calls become `same_as` edges, recovered from aliases whose target exists.
- The `places_override` trigger is removed once the field rules apply edits. Overrides no longer
  cascade with a place.
- `corrections` (0 live) become edits. The stored status becomes derived.

**Step 6: events.**
- Event clusters over `event_catalogue` and compiled events, as in §3.9.
- Event ids per §3.3.4, old ids as `event_aliases` (V053 exists).
- `retrieved_at` stays as information. The version becomes `(release, content sha)`.

**Step 7: media.**
- Provider assets get UUIDv5 uids, with the old uid as an alias in `uid_aliases` (V036) so links and
  bundles keep resolving.
- The point and period become derived. `media_links` and `media_checks` become observations and
  releases.

**Step 8: exchange.**
- Add the bundle's `reference/` section (bump the format version; the importer still reads version 1).
- Replace `BundleReference` with digests.
- Remove the reference-data branches of the merge planner (`place-differs`, `named-period-differs`
  for seed rows). Local places become members of dataset `local` and travel as observations.
- `BundleEventSets.newer` becomes `(hlc|version, node, sha)`.

**Step 9: remove the legacy paths.** Remove `Gazetteer.canonical`'s native-first rule, the
`Placement.Join` bookkeeping, `PointAreaRepo.deletePlacesIn` for imports, the `places_override`
trigger, and `uuidv7()` defaults on reference rows.

---

## 6. Test strategy

### 6.1 Laws (ScalaCheck, pure, in `core`)

- The record store, release set, edit registers, OR-set and same-as registers each satisfy
  `a ⊔ b = b ⊔ a`, `(a ⊔ b) ⊔ c = a ⊔ (b ⊔ c)` and `a ⊔ a = a`. Generators include equal versions
  with different shas, equal HLCs with different nodes, and add/remove races.
- `RefCluster.cluster` is invariant under any permutation of members and edges and under any split
  into batches folded together.
- **Incremental = full**: for a random store and a random change, recomputing only the touched
  candidate components gives exactly the full recompute.
- **Constraint safety**: no output cluster violates C1 to C4. Removing an accepted edge never puts
  two members with distinct own Wikidata ids into one cluster.
- Every field rule is invariant under member order and always produces a value or a defined absence
  (no tie).
- The name key and distance give identical results to the SQL mirror on a corpus of real names, and
  the same values on two JDKs.

### 6.2 Permutation tests (database, `*_test`)

- Synthetic sources S1, S2, S3, each with links, conflicting ids, point-only areas and a dropped
  record in a second version: load them in all 6 orders and with batch sizes 1, 7 and all, plus
  reruns. The derived digest is equal in every case. The Varaždin and point-area fixtures of
  `GazetteerRepoSuite` become cases of this test.
- **Real data at real volume.** Use bbox slices of the pinned extracts (e.g. Anatolia and the Levant)
  for all six orders of {GeoNames, Wikidata, TGN} plus the seed. Then do the whole world on a
  restored `*_test` copy, at least twice in different orders. This follows the "test at real
  volumes" rule.
- Events: GVP, NOAA and Wikidata in all orders; equal event ids and fields.

### 6.3 Convergence tests (two databases)

- Database A loads GeoNames and Wikidata v1 and makes edits e1. Database B loads TGN and Wikidata v2,
  a media provider and an OSM sample, and makes edits e2, one of them concurrent with e1 on the same
  field.
- Exchange A→B, B→A, then A→B again. Check that:
  - both observation and both derived digests are equal;
  - the third exchange changes nothing;
  - the same concurrent-edit conflict is reported on both sides;
  - a record dropped in Wikidata v2 is dead on both sides.
- A relay through a third database C gives the same digests.

### 6.4 Migration tests

On a restored copy of the live database, after each step:
- every place id that existed before (1 818 744), every alias, every attestation, asset link, media
  link and event `place_id` still resolves;
- the count of findings with a place is not lower, and each change is listed;
- the shadow digest equals a full recompute.

### 6.5 Environment

The same store recomputed on two PostgreSQL/PostGIS versions gives the same derived digest. This
check ensures nothing in identity or field rules reads PostGIS geography or a locale collation.

---

## 7. Performance at current and planned volume

Live volumes, 2026-10-28:

| table | rows |
|---|---|
| places | 1 818 744 |
| `place_names` | 17 841 486 (more than the ~10 M estimated) |
| `place_links` | 2 647 893 |
| `finding_places` | 1 371 797 |
| events | 4 247 |
| catalogue records | 4 267 |
| provider media | 3 325 |
| media links | 4 633 |

Source records:

| source | records |
|---|---|
| Wikidata | 1 074 330 |
| GeoNames | 1 150 530 |
| TGN | 38 576 |
| seed | ~60 700 |
| **total** | **≈ 2.32 M** |

World OSM historic and heritage POIs add perhaps 3 to 5 M, for ≈ 6 to 7 M members.

- **Clustering in memory.** Union-find over 7 M members uses `Int` arrays: tens of MB. Edges: ~3 M
  strong or close links today, plus the fuzzy pairs. Sorting edges per component and applying them
  is O(E log E). A full recompute of identity takes on the order of a minute in the JVM. Reading the
  inputs (links and record points) dominates, so stream them in key order.
- **Fuzzy blocking.** Sort by (name key, grid cell). Only records without strong ids take T3 edges,
  which is mostly OSM. Common names in dense cities make the largest blocks, and the diameter cap
  bounds what they can join. Measure the block-size distribution on the OSM extract before fixing
  `d(kind)`.
- **Do not copy names.** The 17.8 M name rows stay where they are, re-keyed to their record. The
  derived `place_id` on them is updated only for members whose cluster changed. A full Wikidata
  release in which few records changed rewrites few rows, because unchanged `content_sha` means no
  work.
- **Storage.** `ref_records` (≈ 2.3 M rows with `parsed` and geometry) adds about 1 to 2 GB before
  TOAST compression. Raw extract payloads are not duplicated in the database. OSM at 5 M rows roughly
  doubles that. Releases and edits are negligible.
- **Read path unchanged.** `places` stays the read model with today's indexes (V075, V076 partial
  indexes, trigram, name key), so search and the map keep their ≤ 100 ms budget. Recompute writes in
  per-component batches and never holds a lock across the whole table.
- **Exchange size.** Pinned extracts travel by sha (≈ 200 MB compressed when sent). API-sourced
  records and edits are small. OSM by reference requires both sides to hold the same extract; else
  it is sent as a blob.

---

## 8. Open questions for the maintainer

1. **Authority ranks and field priorities** (§3.4). Is local < seed < LPF < Wikidata < TGN <
   GeoNames < OSM < registers right for the cluster id, label, kind and period? Should a national
   register outrank Wikidata for a monument's period and kind?
2. **Keep today's ids where possible?** Rank-then-key keeps most `wikidata_q…` ids. Pure min-key
   would rename hundreds of thousands. Step 3 will give the number. Is a one-time rename of N ids
   (all kept as aliases) acceptable?
3. **Fuzzy scope.** Should T3 ever join two records that both carry strong ids of different schemes
   (an OSM POI with `wikidata=` against a register entry without one)? The proposal is no: such
   records join by id only.
4. **Configuration as data.** Which settings move into the stored rules profile: point-area kinds,
   the open-start window, tolerances, ranks? May a database override them, accepting that its derived
   state then differs from its peers?
5. **Licences of exchanged observations.** GeoNames (CC BY 4.0), TGN (ODC-By), Pleiades (CC BY) need
   attribution, which the release rows carry. OSM (ODbL) is share-alike for a derivative database.
   Is exchange by reference only (each side fetches the extract) the default for OSM?
6. **Tombstone and edit history lifetime.** Keep forever, or collect garbage once every known peer
   has acknowledged? There is no peer registry today. The ledger of `merges` could become one.
7. **Concurrent edits.** Is "LWW shows a winner, lint reports the conflict" enough, or should
   concurrent edits of a scholarly field (period, kind) block like a merge issue?
8. **Person merge vs. strong ids.** May a person's same-as join two Wikidata items (C2)? The proposal
   is yes, with T0 above C2 and lint reporting it.
9. **Point-only areas.** Hide (derived visibility, proposed) or drop the observation entirely?
   Hiding keeps their names searchable on any cluster they join.
10. **Live-API versions.** For Wikidata events and media, the retrieval instant is the version, so
    the database that fetched later wins even if upstream regressed. Acceptable, or should such
    datasets carry an upstream revision id where the API gives one?
11. **Rules version mismatch on import.** Accept the observations and mark the derived state stale
    until the server is upgraded (proposed), or refuse the bundle?
12. **Scholarly content.** Sources, findings and articles keep the merge planner, whose
    `keep-left`/`keep-right` decisions are themselves asymmetric (A into B ≠ B into A). Should the
    same edit model (HLC, based-on) replace those decisions later, or does a person always decide
    there?
