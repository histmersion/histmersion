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
  a backfilled person's run (`jobs.origin = person`) has no recorded surface. The write side has no `Unknown` part,
  only the named case `FromBundle`: a row a bundle brings in, whose creator the bundle does not carry yet; it is
  stored as `Unknown(Unrecorded)`, as V113 did for a bundle's queue item.
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
  - a run: the engine gives each pipeline's run `RunCtx.creator`, the run's workflow (agent `Code` today; naming the
    model step's agent on what a model step writes is the next refinement), and a run that starts another (a compiled
    article's review) starts it as itself;
  - background work keeps its starter's creator: an OCR job its enqueuer's (`IngestPending`), a job's body the
    creator in scope where the job was started;
  - an in-process writer that is no workflow definition: its `InTreeWorkflow` case (new cases: `init` (catalogue),
    `adopt-database`, `engine-start` and `approval-effects` (engine), `ingest`, `check-fix` and `agent-tools`
    (library));
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

`ProvenanceSuite` (server tests, beside `ArchitectureSuite`), over every main source:

1. **Every table main code inserts into is classified**: a record table (§3) or one of the not-record kinds, each
   named once, and only tables main code inserts into. A new table that is neither fails, so a new create path
   cannot be added without deciding.
2. **Every INSERT into a record table writes `creator`**, naming `path:line` when one does not.
3. **Each binds a creator the compiler demanded**: the def around it takes a `Creator.New` (a parameter or a `using`
   clause, which every caller must then supply up to a binding or an in-process writer), or the INSERT copies a
   waiting edge's own creator, or it is a bundle's import binding the record's carried creator
   (`StoredCreator.carried(x.creator)`, as built) or `Creator.unrecorded`.
4. The suite's own fixture holds the scan to its reading (a table, its columns, the def's signature).

And the schema agrees: `MigrateSquashSuite` (db tests, on a `*_test` database) checks after V116 that every `creator`
column is NOT NULL with no default, and that an INSERT naming none is refused by Postgres (`23502`). Every record
table's create paths were done in this lane, so no table waits on a pending list.

### 7. How the work is done (mechanical vs judgement)

| slice | mechanical (a committed rule under `tools/refactor/`) | judgement (by hand) |
|---|---|---|
| 1. types | — | `Creator.Automated`'s surface, `Part` surfaces, `StoredCreator` in `util.database` with `read` and `column` |
| 1b. wire move | `RelocateWireCreator` (Scalafix): `Queue.Creator/Agent/CreatedBy/Creation` → `api.Provenance…` at every use; then `RenameSymbols` (`rename-wire-creation.conf`): `api.Provenance` → `api.automation.Creation` | the definitions' move, twice (`api/Provenance.scala`, then `api/automation/Creation.scala`) |
| 2. migrations | — | V115 (columns, backfill), its rehearsal |
| 3. create paths | `CreatorRewrite` (Scalafix, after `WritePermitRewrite`): adds `creator` to each record INSERT's column list, `(using Creator.New)` to the def around it, and follows it up the call graph and across overrides (trait, `Implementation`, `InMemory`), test files getting `TestCreators.given` | each `stop` row: the controller's or worker's provider, the `InMemory` states recording it, the bundle imports' rule |
| 4. surfaces | `AddRecordFields` (the inputs, the views, `Source.Patch.Request`, the bundles and their rows) and `AttributeEndpoints` (the endpoints) | the parse boundary, the multipart inputs, the repositories' reads, Markdown, the CLI flags and stamp, the web card, the model steps, the imports' owners, the approvals, the bundles' export and import |
| 5. check | `CreatorRewrite` with `testFixtures` (`creator-fixtures.conf`): the tests' own fixture INSERTs name an unknown creator | `ProvenanceSuite`, V116 and its schema test |

## As built: slice 4 and its refinements (2026-11-24)

### Write inputs name their creator

- Every REST write body whose endpoint creates a record carries `createdBy` (`Creation.By`: plugin and workflow, and
  the agent's definition, provider and model, all three or none) and `via` (`rest` | `cli` | `web`); it extends
  `Creation.Requested`. Every MCP tool's own input carries `createdBy` and extends `Creation.Named` (the MCP is its
  surface). An upload, whose body is the document, carries them as query parameters (`via`, `byPlugin`, `byWorkflow`,
  `byAgentDefinition`, `byProvider`, `byModel`).
- `domain.CreatorControllers` is the one parse boundary: `rest(in) { … }` and `mcp(in) { … }` give the body the
  creator as a context function, nested inside the binding's `Unattributed` given, so it wins. Over REST a request
  that names nothing is `Unattributed(rest)`, one with `via` and no agent `Manual(via)`, one with `createdBy`
  `Automated(…, via)`. Over the MCP: `Unattributed(mcp)`, or `Automated(…, mcp)`. The work lists keep their own rule
  (`WorkListControllers.creatorOf`: the MCP's `queue add` refuses an item without `createdBy`), built on the same
  parse.
- An endpoint with no body (`refs/{id}?apply`, a vacuum, a restore, a merge) keeps its binding's `Unattributed`
  given; the personal inputs (a bookmark, a note) do not depend on automation (§5).
- The CLI names its caller once, before the command (`histmersion --by-plugin … --by-model … <command>`, else
  `histmersion.cli.agent.*`). Its client stamps every write input that extends `Creation.Requested` with
  `via = cli` and that identity, unless the input names its own. The web client stamps `via = web`.
- Mechanical: `AddRecordFields` (`record-fields-inputs.conf`) gave 67 classes their fields and parent (42 REST
  bodies, 25 MCP inputs). `AttributeEndpoints` (`attribute-endpoints.conf`, `-apply.conf`) wrapped 69 endpoints and
  listed 29 that keep the binding's given. The multipart inputs, the parse boundary and the clients were done by hand.

### Views show who, how and when

A source, a finding, an edge in `neighbors`, an article, an asset, a goal, a run, an approval, an event and a place
carry `creator: Option[Creation]` and `createdAt` (`record-fields-views.conf`; a run and an approval had their times
already). A source's `createdAt` is its `added_date`, a day. Each repository reads `creator::text` through its own
`Jsonb.codec` given (no shared codec: `util.database` does not name the automation domain), and a domain row such as
the asset's names no api type, so its repository reads the two columns beside the row. `Creation.line` renders
"Created by … via … at …" for the Markdown (CLI and MCP) and the web page: the reading pane's head, the place and event
cards, the approvals, the run detail, the goal page. A note and a bookmark store their creator and show none.

### Imports name their plugin

An event catalogue's, a gazetteer source's and a media collection's rows are `Automated(plugin, workflow, Code,
internal)`: the plugin is the owner of the catalogue, source or collection (found by instance among the plugins'
contributions, the in-tree ones as `catalogue` or `media`), and the workflow is `event-import`, `gazetteer-import` or
`media-import`.

### Bundles carry creators

The twelve stamped records of a bundle (source, finding, article, article version, asset, asset link, attestation,
event, goal, queue item, place, period) carry `creator: Option[Json]`, the stored document as it is (as a goal's
plan travels). The bundle format is a supporting area and names no domain's type, so it does not use `Creation`. The
export reads it from the row (`record-fields-bundles.conf`, `record-fields-bundle-rows.conf`). An import writes
`StoredCreator.carried(record.creator)` on the rows it inserts. That is the document when it reads as a stored creator,
else `Unknown(Unrecorded)`, as for a record without one. A row already here keeps its own creator, because the
creator records who made the record and not who merged it. `ProvenanceSuite` accepts that binding beside
`Creator.unrecorded`. The rows an import makes of its own (a version archiving a text the merge replaced, a parked
edge, an assessment, a candidate, a correction, a goal's links and schedules) stay `Unknown(Unrecorded)`. The creator
is provenance, not content, so `BundleFormat.Unrevved` leaves it out of the `rev`: the merge ledgers' revs and an
unchanged record's match stay as they were. The format moved to 2, so a build that reads only format 1 refuses a
bundle whose creators it would drop. A format 1 bundle still imports.

### Request metadata never lives in a stored or checkpointed content type

The rule (coordinator's decision of 2026-11-24): `createdBy` and `via` say who asked and how, which is not part of what
was asked. A type that is also stored or checkpointed keeps its stored form, and the request carries the metadata
beside it in a request type of its own. Of the write inputs, one is stored this way: a source's patch, which an
agent's run checkpoints (an enrichment's proposal holds one; `CheckpointGoldenSuite` keeps old checkpoints reading
back byte for byte). Its REST body is `Source.Patch.Request`, the patch's own fields flat (the queue's `QueueAdd`
convention) plus `createdBy` and `via`; the route hands `request.patch` on, and `Source.Patch` is what it was. The
CLI and the web page send `Source.Patch.Request.of(patch)`; the MCP `patch_source` input already held the patch beside
its own `createdBy`. The codecs write every `None` as `null` (ADR 0026), so a field cannot be added to a stored type
without changing its stored form.

### A model step's writes name their model, others are the run's

The rule (coordinator's decision of 2026-11-24): a write is credited to the model whose answer produced it, never to
"the last model that answered" — a run with several model steps (a draft, then a review) would otherwise credit the
drafter's writes to the reviewer.

- A record one model step's answer produced names that step's model: `Automated(plugin, workflow, Model(definition,
  provider, model), internal)` — the step's agent definition, and the provider and model the chain picked for the
  attempt whose answer passed its checks. The step's context records it (`RunCtx.answered(step)`) and the code that
  writes that answer runs under it (`RunCtx.byAnswer(step) { … }`): a goal's plan and its approval (plan-goal,
  `propose`), a hint's effect (hint-effect, `propose`), an article's filter (infer-filter, `propose`), a plan of
  articles (propose-articles, `propose`), the sweep's seen, ingested, parked and queued picks (acquisition-sweep,
  `rank`), a compiled article (compile-article, `draft`), a review round's patch (review-article, `fixer-<n>`).
- A write no one step's answer produced is **the run's**: `Automated(plugin, workflow, RunModels(definition),
  internal)`, a model agent whose provider and model are not tied to the write; its definition is named when the
  workflow's steps use exactly one (`WorkflowDefinition.stepDefinitions`), else none. A workflow with no model step
  is `Code`. Nothing is guessed: a replayed step (a resumed run) has no answer in this process, so its writes are the
  run's, and so is a write whose model's names do not pass their rules. Stored, `RunModels` is
  `agent: {kind: model, definition?}` with no provider or model.

### Approval effects and agent tools act as their caller

- An approval stores the creator that proposed it (the proposing step's model, or the run's), and approving it
  applies its effect under that creator (`ApprovalService.creatorOf`, read back through `StoredCreator.recorded`): the
  status, the edge, the article filter or the plan the run proposed is the run's, the person only decided it. An
  approval whose creator does not read back (one recorded before V115, a partial one) is applied under the decider's.
  A proposal the run applies at once (an `auto` mode) is the run's directly. The `approval-effects` in-tree workflow
  is no longer used for either.
- An agent tool is made for its caller (`ToolContribution.agentTool(using Creator.New)`): the in-process agent loop
  hands each step's tools that step's creator (its definition, the provider and model of the attempt). The agent MCP
  endpoint the command-line runtimes call (`/mcp/agent/<agent>`) is built once per definition and cannot see the
  calling run's token, so its tools still act as `agent-tools`; they only read.

## Consequences

- No create path compiles without a creator in scope, and after V116 none runs without one.
- A bundle carries its records' creators (format 2). A record from a bundle that carries none is stored as
  `Unknown(Unrecorded)`, as V113 did for queue items.
- The in-tree contributors' names (`engine`, `library`, `research`, `catalogue`, `media`) stand until the workflows
  move into plugins of their own; then the plugin's id replaces them on new rows, and old rows keep theirs.
- Row 18 (problem cases) takes V114 for the jobs table; this record's migrations are V115 and V116.
