# 0010. Conventions of the shared values and vocabularies

- Status: accepted
- Date: 2026-11-21

## Context

The `values` module holds the newtypes and closed vocabularies every layer shares. Some of their meaning cannot be read
from a name: a sign convention, a counting rule, a rule mirrored in SQL, a wire spelling another component depends on.

## Decision

**Kernel values**
- `Year` is signed, BCE negative (3300 BCE is `Year(-3300)`). The calendar has no year 0, but the corpus counts
  astronomically, so every integer is a year.
- `LatLon` is a point on WGS84, as PostGIS's `geometry(Point, 4326)` holds it; `Latitude` is degrees north (negative:
  south), `Longitude` degrees east (negative: west).
- `UserId`: while the server runs for one person, every bookmark, note and read mark belongs to the one local user.

**Library**
- A **citekey** is lower-case, starts with a letter, then letters, digits, `_`, `.` or `-`, 2 to 121 characters, and
  never contains `..`. It is a file name on the server (the ingest and acquisition spools keep `<spool>/<citekey>`) and a
  URL segment, so it is bounded and can never climb out of a directory. Before it was validated, `../../../../tmp/x`
  became a directory. The corpus convention is `author2023_word`; `-` and `.` are admitted because real citekeys use
  them.
- A **DOI** is kept lower-case, because DOIs are case-insensitive; a text's resolver URL, `doi:` prefix and trailing
  sentence punctuation are dropped.

**Licences** (one classification shared by pictures, sources' open-access licences and datasets)
- A licence is stored and shown by its id (an SPDX id, `public-domain`, or the statement itself when it reads as no
  known licence) and classified into a family, which is what an allowlist names, so a new version of an allowed licence
  needs no config change.
- Two families are never stored, whatever a config says: an unreadable licence and all rights reserved.
- A licence given as a URL is read only as a Creative Commons URL; another site's terms page is never guessed.
- A public-domain picture still shows its credit line, as a courtesy and a source.

**Catalogue**
- A place id is the gazetteer, an underscore and that gazetteer's id (`geonames_2911611`).
- The store recognises five place kinds. The column is free text (an importer may carry its own vocabulary), but these
  are what the CLI and UI offer and what foreign type labels map onto. A `people` (a people or a civic tribe, such as the
  Ubii or the Athenian tribe Aiantis) is placed at a representative point, never an area.
- A Pleiades place whose only location is a square of the Barrington Atlas grid gets the centre of that square as its
  point, which says "somewhere in this square", not "here" (Thessalia at 37.5 N 22.5 E lies in the Peloponnese). Such a
  point is told from the coordinates alone, because the seed keeps only the representative point: a Pleiades point that
  is the centre of a 5°, 1° or ½° cell is approximate. On the live corpus (2026-10-09) 2,426 Pleiades points were, 2,363
  of them "rough" in Pleiades' own dump, and no precise point sat exactly on a centre at five decimals. Only Pleiades
  rows count: a Wikidata coordinate in whole minutes is coarse but no atlas square. `places.location_precision` is the
  SQL twin (a generated column), held equal by `PlaceCleanupSuite`.
- A picture's roles show on a card in a fixed order: a place's own picture before an object found there and a map of it;
  a source's cover before an illustration.

**Automation**
- An approval kind is either blocking (the run waits in `waiting_approval` and applies the change itself when it
  resumes) or applied by the decision (the run never waits). A structured action a model proposed from a hint is applied
  only when a person approves it.
- Who decided an approval is read from the marks its note and result start with (the autopilot's note mark; the result
  of an `auto` mode). The SQL that filters and counts by decider must say the same as `DecidedBy.of`.
- A run is a background job, so its id is that job's id. `Paused` is resumable (a budget breach, a server restart, a
  person's pause); `WaitingApproval` holds no worker and goes back to the queue when the approval is decided; the other
  terminal states are final.
- A manager drives `review` (library-wide: articles until their review converges), `enrich` (library-wide: sources until
  enriched), `seed` (one research area: articles proposed, approved and compiled) or `watch` (one research area: an
  acquisition sweep and a currency check on its cron). `paused` starts nothing new; `stopped` also cancels in-flight runs
  and items.

**Exchange (bundles and merges)**
- In a merge decision, *left* is this collection and *right* is the bundle.
- An issue kind's wire name is part of the issue id, so it never changes. A decision an earlier `merge apply` made for
  the very same issue is remembered, as git's rerere does, so a decision is never asked twice.
- Bundle entity kinds are kebab-case (`article-version`), as the database trigger writes them. A bundle carries only the
  places and periods added locally; the bundled seeds every instance imports itself are not carried.

## Consequences

- These rules are invariants: changing one (the citekey rule, a wire name, a rule that has an SQL twin) needs the stored
  data and the twin changed with it.

## Code

`modules/values/src/main/scala/com/histmersion/` (`Year`, `LatLon`, `Latitude`, `Longitude`, `UserId`),
`domain/library/` (`Citekey`, `Doi`), `domain/Licence.scala`, `domain/catalogue/Place.scala`,
`domain/media/MediaRole.scala`, `domain/automation/` (`Approval`, `Run`, `Manager`), `util/data/exchange/`
(`DecisionKind`, `Issue`, `Bundle`); `modules/values/src/test/scala/com/histmersion/values/CitekeySuite.scala`.
