# 0018 — The research state is computed from stored, typed acquisition attempts

Status: accepted (owner decisions 2026-11-20, queue row 22). Design: `notes/design-research-state.md` (coordinator's notes).

## Context

Research › State shows, for every open item, who acts next: the person or the agents, and why. For an acquisition it
must tell "a provider will fetch it" from "no free legal copy found: buy or borrow", "download it in the browser" and
"borrow it". Before this record the reasons were sentences (`acquire_checks.reason`, one row per item, each check
overwriting the last), and a fetcher's refusal was one untyped case (`Fetch.Refused(why)`), so the page could only have
guessed by reading text.

## Decision

1. **Fetchers answer typed (SPI 3).** `Fetch.Miss(absence, why)` with `Absence` = `NoIdentifier | NotHeld | Unanswered |
   RateLimited | Unconfigured`; `Fetch.Refused(refusal, why)` with `Refusal` = `BotChallenge | LendingOnly |
   NoEntitlement | LicenceForbids`. `Fetch.failed(problem)` maps a failed request to the answer it stands for, in one
   place. The host carries the answer as `Problem.NotFound.Said` in `Problem.NotFound.Answer`. `plugin-api-api.txt`
   pins the SPI surface under its version (`SpiSurfaceSuite`, G99). A problem entry stored before the change, of the old
   `Copy` shape, no longer decodes as its case and reads as its sentence (the existing unknown-entry path); nothing
   stores those entries for long, and nothing branches on them.
2. **Every attempt is kept.** `acquire_attempts` (migration V112) replaces `acquire_checks`: one row per attempt, the
   newest `acquire.recheck.attempts-kept` (20) per item, each with its trigger, fetcher generation, outcome, every
   fetcher's typed answer (`answers`), the failure (`failure`) and the open-access facts the metadata gave (`oa`). The
   JSON columns have a stored shape of their own, defined in SQL by `AcquireCheckRepo` (G91): `answers` is an array of
   `{fetcher, said, why}` with `said` a stored word (`not-held`, `bot-challenge`, …) and `why` a problem entry,
   `failure` the problems column's entry array, `oa` `{scripts, status, url, licence}`. The repository builds and reads
   them from typed SQL values, so no wire codec reaches the store; a row that does not read is shown as a legacy
   attempt and is never rewritten.
3. **The old checks become legacy attempts.** Each `acquire_checks` row becomes one attempt with outcome `legacy`, its
   reason text kept verbatim in `legacy_reason` and its count in `legacy_checks`, under the generation `legacy`. Since an
   attempt under another generation makes an item due (`AcquireRecheck.dueAt`), the scheduled re-check checks every
   item once more within its daily caps and the shared allowance. That is the owner's "re-check the list once", with no
   separate job.
4. **The verdict is a pure function, not a stored column.** `AcquireVerdict.of(identifier, attempts, generation,
   settings, now)` (core) decides `Unchecked | Converting | Retrying` (the agents) or `Stuck | ManualDownload | Borrow |
   NoFreeCopy | NoIdentifier` (the person). It depends on the clock, the fetcher generation and the give-up threshold
   (`acquire.recheck.give-up-checks` 5 / `give-up-days` 14), so storing it would make it stale. Only the legal chain's
   attempts are recorded, so an unauthorised fetcher never makes an item the agents'.
5. **Tasks carry their actor.** `queue_items.actor` (`person | agents`, V113) is required for every open task
   (`CHECK (list <> 'tasks' OR status <> 'open' OR actor IS NOT NULL)`). The migration sets `agents` for the reviewer's
   own `agent review` items and `person` for every other item (a one-time reading of the stored topic). Afterwards an
   item's actor is set when it is created, from its creator (`Manual` → person, `Automated` → agents), and changed only
   explicitly (`queue patch <id> --actor`, `PATCH /api/queue/items/{id}` with `actor`, the web page's toggle).
6. **Every work-list item records who created it** (owner decision 2026-11-22, queue row 32; queue items first).
   `queue_items.creator` (V113, `jsonb NOT NULL`, no default) holds the domain's `Creator`:
   - `Manual(surface)`: the person, through `rest | mcp | cli | web | internal`. `internal` is a record the server writes
     itself as part of a person's operation that reaches it without a surface of its own: a merge's follow-up tasks and
     worklog line, a source deletion's worklog line, a person's bulk decision's worklog line.
   - `Automated(plugin, workflow, agent)`: a named plugin and a named workflow (both required), and the agent as
     `Model(definition, provider, model)` or `Code` (a step that calls no model). The names are newtypes checked by
     their rules: `Creator.Plugin.Name`, `Workflow.Name`, `Definition.Name` (the agent definition), `Provider.Name`
     (the AI provider) and `LanguageModel.Name`, each read from a wire value by its companion's `from`.
   - `Unknown(facts)`: only what the migration could not attribute from stored facts — `Unrecorded(job)` (made before
     creators were recorded; the job it names, if any) — and `Unreadable(stored)`, a stored document this build cannot
     read. The wire's `Queue.Creator` has the stored document's own shape (`kind`, `surface`, `plugin`, `workflow`,
     `agent{kind, definition, provider, model}`, `job`), so a read decodes the column straight into it, no name is
     unwrapped and rebuilt on the way, and a document that does not decode shows as kind `unreadable`; the row keeps
     what it holds and is never rewritten.
   When is `created_at`. Each part of a stored `Automated` is `Known(name) | Unknown`, and only the migration writes an
   `Unknown` part: the write path takes `Creator.New` (`Manual(surface) | Automated(plugin, workflow, agent) |
   Unattributed(surface)`), which has no unknown, so no new write can carry one. `Unattributed(surface)` is the named
   case for a write that arrives through a surface that cannot carry an identity yet — an ingest's browser-download item,
   over MCP (agents only, so never `Manual`) or over REST (the web page and the CLI both call it, so a person cannot be
   told from a script) — so the State tab can say "creator not recorded" instead of guessing. The migration reads stored facts only: an item whose `created_job` names a
   job with a `pipeline` becomes `Automated(Unknown, Known(pipeline), Unknown)`; every other item `Unknown(Unrecorded)`.
   A bundle's queue line carries no creator yet (row 32), so an imported item that is new here is stored as
   `Unknown(Unrecorded)` with the actor the migration's rule gives; an item already here keeps its own.
7. **Who an outside caller is, at one parse boundary.** `QueueAdd.createdBy` (`api.automation.Queue.CreatedBy`:
   `plugin`, `workflow`, `agentDefinition`, `provider`, `model`) and `QueueAdd.via` (`cli | web | rest`) are read by
   `WorkListControllers.creatorOf`: plugin and workflow both, or no record; the agent's three names all, or none
   (`Code`); a partial record or a name its rule refuses is a typed `Invalid`. Without a record the item is
   `Manual(via)` (`rest` when the client names none); an MCP add without one is refused (`Invalid.Missing`), since an
   agent is the MCP's usual caller. The CLI sends `via = cli` and the identity from `--by-plugin --by-workflow
   --by-agent --by-provider --by-model`, each else `histmersion.cli.agent.*` (`HISTMERSION_AGENT_PLUGIN`, `_WORKFLOW`,
   `_DEFINITION`, `_PROVIDER`, `_MODEL`); the web page `via = web`. An ingest that lists a browser download takes its
   creator from its door: `Unattributed(rest)` for `POST /api/acquire/ingest`, `Unattributed(mcp)` for the MCP tool (the
   ingest request carries no identity yet, row 32), and the calling workflow for the legal re-check and the watchers.
8. **In-process writers name their workflow.** Every `WorkflowDefinition` names its `contributor`, the plugin it comes
   from, and gives `creator(agent)`; a run's writes take `RunCtx.creator` (the run's workflow, agent `Code`, since the
   items are written by code steps). Every `ManagerKindDefinition` names its `creator`. The engine's own processes that
   are no workflow definition are the closed set `InTreeWorkflow` (run notices, scheduler, autopilot, auto-approvals,
   approval digest, drain, goal focus, managers, the acquisition re-check, the ingest backlog, the seed). The in-tree
   contributors are named `engine`, `library` and `research`: these stand as the plugin names until the workflows move
   into plugins of their own.
9. **No sentence is read back.** The text readers of the re-check (`AcquireRecheck.replies`, `reason`'s parsing) are
   deleted. The page's wording comes from the types.

## Consequences

Plugins must be rebuilt against SPI 3. A provider that keeps failing becomes the person's after the threshold, with the
last problem shown. An unreadable stored attempt is shown as legacy text and makes the item due, never a default.
