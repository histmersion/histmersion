# 0029. Every created record names its creator: the person, or a named (plugin, workflow, agent)

- Status: accepted (owner decision of 2026-11-22; queue row 32)
- Date: 2026-11-23
- Number: 0028 is taken by a lane in flight (the declared proxy client address), so this record is 0029.

## Context

The owner decided on 2026-11-22: "we define multiple workflows, and each workflow is defined by plugins, we should
require that both workflows and plugins are named, same with agents, so that we could declare whether something was
created by user manually or by a triple (plugin, workflow, agent) — perhaps agent should also be identifiable with
(agent definition, ai provider, model) to give us enough information to determine who creates something (in addition
to when and how)".

Row 22's lane did it for the work lists first (ADR 0018 items 5–8, migration V113): the kernel's `domain.Creator`
(`Manual(surface) | Automated(plugin, workflow, agent) | Unattributed(surface) | Unknown(facts)`), its write side
`Creator.New` (no `Unknown`), the checked names (`Creator.Plugin.Name`, `Workflow.Name`, `Definition.Name`,
`Provider.Name`, `LanguageModel.Name`), the stored document (`StoredCreator`, in the work lists' package), the wire
record (`Queue.Creator`), one parse boundary for an outside caller (`WorkListControllers.creatorOf`, `createdBy` +
`via`), the CLI's `--by-*` flags and `histmersion.cli.agent.*`, and the in-process writers' names (`InTreeWorkflow`,
`WorkflowDefinition.contributor`, `RunCtx.creator`, `ManagerKindDefinition.creator`). This record generalises that one
shape to every table a create operation writes. It does not invent a second shape.

Every write transaction already holds a `WritePermit` (X4/G89), demanded by type, so a write cannot be opened without
one. The creator follows the same pattern: a context parameter the type checker demands, threaded by a committed
Scalafix rule rather than by hand.

## Decision

### 1. The types (core, the kernel `domain.Creator`)

- **`Creator.Automated` gains its surface**: `Automated(plugin, workflow, agent, surface)`, and `Creator.New.Automated`
  likewise. "How" is a property of every write: an agent that calls the MCP is `Automated(…, mcp)`, the same agent
  through the CLI `Automated(…, cli)`, a step of a run in the server `Automated(…, internal)`. The stored `Automated`'s
  four parts are each `Part.Known | Part.Unknown`, and so is the stored `Manual`'s and `Unattributed`'s surface, since
  a backfilled person's run (`jobs.origin = person`) has no recorded surface. The write side has no `Unknown` part.
- **`Creator.Surface`** stays `Rest | Mcp | Cli | Web | Internal`. The owner's "API" is `rest`, "scheduler" is
  `internal`: the server's own processes (the scheduler, the managers, a run's steps, a merge's follow-ups).
- **The names are required and typed.** A plugin is named by its `Plugin.Info.Id` (the SPI's id, checked by
  `Creator.Plugin.Name`'s rule when read); an in-tree contributor by `engine`, `library`, `research` (ADR 0018 item 8),
  and from this record also `catalogue` and `media`; a workflow by its `WorkflowId` or its `InTreeWorkflow` case; an
  agent by `Model(definition, provider, model)` or `Code`. A run whose steps call a model names that model's
  definition, provider and model on the records its model steps write.
- **The write side is a context parameter**: a repository method that inserts a record takes `(using Creator.New)`,
  and so, transitively, does every method that calls one outside a provider. The compiler then refuses a create path
  without a creator, exactly as it refuses a write without a permit. The work lists' explicit `creator: Creator.New`
  parameter stays as it is (it is handed on as the given).
- **Providers of the given** (where a creator comes from):
  - a controller method takes the given too; its binding provides it. A `Routes` or `Tools` class gives its
    endpoints `Unattributed(rest)` or `Unattributed(mcp)` (the request names no identity yet: the web page, the CLI and
    a script all call the same REST endpoint, as ADR 0018 item 7 found for the ingest), and an endpoint whose input
    carries `createdBy`/`via` gives what they say: `Manual(web)` for the web page, `Manual(cli)` or the CLI's
    identity (`Automated(…, cli)`), the MCP caller's `createdBy` (`Automated(…, mcp)`, refused without one where V113
    refuses it);
  - a run: `RunCtx.creator`, the run's workflow with the agent of the step that writes (`Code` for a code step, the
    step's model for a model step);
  - an in-process writer that is no workflow definition: its `InTreeWorkflow` case (new cases: `event-import`,
    `gazetteer-import`, `media-import`, `bundle-import`, `merge`, `ingest`);
  - tests: `TestCreators.given` (a `Manual(internal)` of the test sources), imported like `TestPermits.given`.
- **One parse boundary for outside callers** generalises `WorkListControllers.creatorOf` to
  `domain.CreatorRequest` (server): `createdBy` (plugin and workflow both, or no record; the agent's three names all,
  or none → `Code`) and `via`. Every write input that an agent may send gains the optional `createdBy`.

### 2. The stored document and the columns

- `StoredCreator` moves from the work lists to `util.database` (db module), with its reading back (`read`: a document
  this build cannot read is `Unknown(Unreadable(text))`). It writes the same jsonb document V113 writes (`kind`,
  `surface`, `plugin`, `workflow`, `agent{kind, definition, provider, model}`, `job`). A repository binds
  `${StoredCreator.column(creator)}::jsonb` (no hand-written `DbCodec`: `ContractsSuite` rule `derives`) and reads the
  column into the wire record without unwrapping a name.
- Each **record table** (below) gets `creator jsonb NOT NULL` and, where it has no date of its making at all,
  `created_at timestamptz` (findings, edges, candidate watches, articles, review ledgers, assets, asset links, events,
  places, periods). A table with one keeps it as the "when" (`sources.added_date`, `assessments.checked_at`,
  `source_candidates.first_seen`, `event_link_candidates.computed_at`, `created_at` elsewhere). An existing row's new
  `created_at` is NULL; new rows default to `now()`. No date is invented.
- An edge waiting for its target (`unresolved_edges`) carries the creator of the edge, and keeps it when the edge is
  resolved, so the edge's creator is who made the edge, not who added the source it points at.
- V115 adds the columns with the backfill and a temporary default (`{"kind":"unknown"}`), so every slice between it
  and the create paths stays green. V116, in the slice of the architecture check, drops the default: from then on an
  INSERT that does not name `creator` fails in Postgres too.

### 3. The record tables, and the ones that are not records

A record is a row a create operation makes as a thing a person can address (an id, a uid, a citekey or a slug), so
the person may ask who made it.

| area | record tables (carry `creator`) |
|---|---|
| library | `sources`, `findings`, `edges`, `unresolved_edges`, `assessments`, `source_candidates`, `candidate_watches` |
| research | `articles`, `article_versions`, `review_ledgers` |
| media | `assets`, `asset_links` |
| catalogue | `events`, `places`, `periods`, `attestations`, `corrections`, `place_overrides`, `event_link_candidates` |
| automation | `queue_items` (V113), `goals`, `goal_links`, `goal_schedules`, `approvals`, `jobs` (runs), `agent_managers`, `manager_hints` |
| personal | `notes`, `bookmarks` |

Not records, each by kind (the check lists every table that main code inserts into under one of these):

- **part of a record**, made with it in the same create and attributed through it: `event_datings`, `event_edges`,
  `event_participants`, `place_names`, `place_links`, `place_relations`, `place_types`, `place_descriptions`,
  `media_links`, `article_links`, `article_critique_reasons`, `source_identifiers`,
  `finding_places`, `manager_items`, `manager_hint_uses`, `goal_focus`;
- **derived** (rebuilt from records): `source_chunks`, `place_footprints`, `site_undated_cells`, `site_undated_build`,
  `land`, `map_changes`;
- **identity bookkeeping**: `source_aliases`, `article_aliases`, `goal_aliases`, `event_aliases`, `place_aliases`,
  `uid_aliases`, `tombstones`, `alias_tombstones`, `place_removals`;
- **state, caches and quotas**: `agent_state`, `agent_leases`, `agent_allowances`, `agent_autopilot`,
  `agent_role_chains`, `agent_role_fallthrough`, `upstream_quota`, `plan_pools`, `plan_quota_readings`,
  `plan_quota_refreshes`, `ui_seen`, `gaz_seen`, `retraction_watch`, `seed_versions`, `instance`, `media_checks`,
  `event_link_checks`, `event_import_parts`, `gazetteer_sources`, `event_catalogue`, `acquire_checks`,
  `acquire_attempts`, `read_marks`;
- **logs and the merge machinery**: `log_events`, `log_writes`, `log_counts`, `manager_events`, `agent_steps`,
  `agent_messages`, `merges`, `merge_sessions`, `merge_ledger`, `merge_decisions`, `flyway_schema_history`.

### 4. Backfill: stored facts only

The migration reads only what a row or the row it names records; everything else is `Unknown(Unrecorded)`.

| table | stored fact | creator |
|---|---|---|
| `jobs` | `origin = person` | `Manual(?)` |
| `jobs` | `origin = scheduler` | `Automated(engine, scheduler, code, internal)` |
| `jobs` | `origin = manager` | `Automated(engine, managers, code, internal)` |
| `jobs` | `origin = run` and `parent_job` names a run with a `pipeline` | `Automated(?, <parent pipeline>, ?, internal)` |
| `approvals` | `job_id` names a run with a `pipeline` | `Automated(?, <pipeline>, ?, internal)` (V113's rule) |
| `events` | `catalogue` set | `Automated(<catalogue's plugin>, event-import, code, internal)` |
| `places` | `import_source` set | `Automated(<import source>, gazetteer-import, code, internal)`, when the name passes `Plugin.Name`'s rule |
| `assets` | `provider` set | `Automated(<provider>, media-import, code, internal)`, likewise |
| `queue_items` | V113's | unchanged; its `Automated` gains an unknown surface |
| every other row | — | `Unknown(Unrecorded)` |

`?` is `Part.Unknown`. A name that does not pass its rule is not stored as a name: the row is `Unknown(Unrecorded)`.
`updated_at` is not touched (the triggers are disabled around the update, as in V113). The rehearsal runs on a `*_test`
database seeded with synthetic rows of each fact, and its counts are in the slice's commit message.

### 5. The wire

`Queue.Creator`, `Queue.Agent`, `Queue.CreatedBy` and `Queue.Creation` become one record of their own,
`api.automation.Creation` (with `Creation.Agent`, `Creation.By` — the input — and the vocabulary on its companion), which
every domain's view may show (the domains depend on automation; personal does not, so a note's and a bookmark's
creator is stored, not shown). Its name is neither `Creator` (a server file converting between the two would need a
qualifier) nor `Provenance` (`api.library.Provenance` is how a source's copy was acquired). A record's view gains
`creator: Option[Creation]` and `createdAt` where it had none (a source, a finding, an edge in `neighbors`, an
article, an asset, a goal, a run, an approval, an event, a place). Markdown renders one line, "Created by …, via …, at …"; the web page shows it in the record's card. The CLI
sends `via = cli` and its identity (`--by-*`, `histmersion.cli.agent.*`) on every write command that creates a record;
the MCP write tools take `createdBy`.

### 6. The check

`ProvenanceSuite` (server tests, beside `ArchitectureSuite`), over main sources and the migrations:

1. **Every table main code inserts into is classified**: a record table (§3) or one of the not-record kinds. A new
   table that is neither fails, so a new create path cannot be added without deciding.
2. **Every INSERT into a record table writes `creator`**, and the def around it takes a `Creator` or `Creator.New`
   (a parameter or a `using` clause). A repository method that writes a record without one fails, naming
   `path:line`.
3. **The schema agrees** (on the test database): every record table has `creator jsonb NOT NULL` with no default.
4. Until a record table's create paths are all done, it is in the suite's `pending` list (one row per table, the slot
   that does it); a row may only be removed, and a table on it is still held to rules 1 and 3 except the default.

### 7. How the work is done (mechanical vs judgement)

| slice | mechanical (a committed rule under `tools/refactor/`) | judgement (by hand) |
|---|---|---|
| 1. types | — | `Creator.Automated`'s surface, `Part` surfaces, `StoredCreator` in `util.database` with `read` and `column` |
| 1b. wire move | `RelocateWireCreator` (Scalafix): `Queue.Creator/Agent/CreatedBy/Creation` → `api.Provenance…` at every use; then `RenameSymbols` (`rename-wire-creation.conf`): `api.Provenance` → `api.automation.Creation` | the definitions' move, twice (`api/Provenance.scala`, then `api/automation/Creation.scala`) |
| 2. migrations | — | V115 (columns, backfill), its rehearsal |
| 3. create paths | `CreatorRewrite` (Scalafix, after `WritePermitRewrite`): adds `creator` to each record INSERT's column list, `(using Creator.New)` to the def around it, and follows it up the call graph and across overrides (trait, `Implementation`, `InMemory`), test files getting `TestCreators.given` | each `stop` row: the controller's or worker's provider, the `InMemory` states recording it, the bundle imports' rule |
| 4. surfaces | — | `createdBy` on the write inputs, the views, Markdown, the CLI flags, the web card |
| 5. check | — | `ProvenanceSuite`, V116 |

## Consequences

- No create path compiles without a creator in scope, and after V116 none runs without one.
- A bundle carries no creator yet: a bundle's new row is stored as `Unknown(Unrecorded)`, as V113 did for queue
  items; carrying it in the `.hmx` format is its own change (the bundle's version moves).
- The in-tree contributors' names (`engine`, `library`, `research`, `catalogue`, `media`) stand until the workflows
  move into plugins of their own; then the plugin's id replaces them on new rows, and old rows keep theirs.
- Row 18 (problem cases) takes V114 for the jobs table; this record's migrations are V115 and V116.
