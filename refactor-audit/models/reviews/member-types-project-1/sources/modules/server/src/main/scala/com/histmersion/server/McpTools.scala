package com.histmersion.server

import com.histmersion.api.exchange.EditionsPolicy

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.automation.AgentStateMarkdown
import com.histmersion.api.automation.GoalsMarkdown

import chimp.server.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.{Api, Markdown, Paging, StrictJson}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.util.mcp.McpOut
import com.histmersion.api.aliases.{Aliases, LabelRename}
import com.histmersion.api.automation.{ActivityMarkdown, AgentsMarkdown, AgentStatePut, ListFilters}
import com.histmersion.api.exchange.{MergeMarkdown, MergePolicy, MergeResolution, MergeResolutions}
import com.histmersion.api.library.{IngestMarkdown, RepairAcquire}
import com.histmersion.api.settings.{ConfigEditMarkdown, ReloadMarkdown}
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.automation.Goal
import com.histmersion.api.exchange.MergePlan
import com.histmersion.api.maintenance.Vacuum

/** MCP tools (Chimp). Every tool returns Markdown and delegates to the same controllers as REST ([[ServerWiring]]). */
final class McpTools(
    h: ServerWiring,
    acquire: com.histmersion.domain.library.acquisition.AcquisitionControllers,
    collections: com.histmersion.domain.library.collections.CollectionsControllers
):
  import McpTools.*

  private val ping =
    tool("ping")
      .description("Liveness check for the Histmersion MCP server")
      .input[PingInput]
      .handle(_ => ToolResult.text(s"${h.instance.version.name} ${h.instance.version.version} — ok"))

  // ── ingest, as a JOB (plan §1.8) ────────────────────────────────────────────────────────────
  private val ingest =
    tool("ingest")
      .description(
        "OCR a PDF/EPUB into a source and keep its figures. Give EITHER `path` (a path the SERVER " +
          "can see) OR `base64` (the document itself, for smaller files) plus a `citekey`. This " +
          "returns immediately with a job id, because converting a long scan takes minutes: poll " +
          "`job_status` until it is done|failed. `quality` runs the slow, accurate OCR path (use it " +
          "for image-only scans); `force` accepts a file already stored under another citekey. When " +
          "the citekey already exists only its body and figures are replaced — findings, tags and " +
          "notes are left alone — so re-OCR never costs enrichment. Optional title/authors/year/doi " +
          "are used ONLY when the source is being created. `acquireItem` names the acquisition-queue " +
          "item this document fulfils: the job ticks it off (`ingested as [[citekey]]`) when it " +
          "succeeds, and leaves it open when it fails. Without it, the job ticks off every open " +
          "acquisition item that names the source's DOI, and lists items with a similar title in its " +
          "progress as possible matches — those are never closed for you."
      )
      .input[IngestInput]
      .handle { in =>
        h.ingest.controllers.fromTool(
          in.citekey,
          in.path,
          in.base64,
          in.filename,
          in.quality,
          in.force,
          in.title,
          in.authors,
          in.year,
          in.doi,
          in.acquireItem
        ) match
          case Right(a) =>
            ToolResult.text(s"queued job `${a.jobId}` for **${a.citekey}** — poll `job_status` with that id")
          case Left(e) => McpOut.error(e)
      }

  private val jobStatus =
    tool("job_status")
      .description(
        "Where an OCR job got to: queued|running|done|failed, with its progress line or the reason " +
          "it failed. Give `id` for one job, or omit it to list the recent ones — filtered on the server by " +
          "`status`, `kind` (ingest, reocr, agent, media-import, …), `since` / `until` (2026-10-01, an ISO instant, or " +
          "7d / 12h ago) and `subject` (text in what it is about); the listing heads with the counts per kind. A job that says 'interrupted' was killed by a server restart — re-run the " +
          "same ingest, it is idempotent."
      )
      .input[JobStatusInput]
      .handle { in =>
        in.id.map(_.trim).filter(_.nonEmpty) match
          case Some(id) =>
            h.jobs.job(id) match
              case Right(j) => ToolResult.text(IngestMarkdown.job(j))
              case Left(e)  => McpOut.error(e)
          case None =>
            val f = in.filter
            ToolResult.text(
              h.jobList
                .page(f, java.time.Instant.now(h.clock), in.limit, in.before, None)
                .fold(
                  McpOut.refusal,
                  (js, page) =>
                    IngestMarkdown.jobs(
                      js.toVector,
                      page,
                      f.qualifier,
                      Paging.olderLine(page, c => s"call `job_status` again with the same filters and `before: \"$c\"`")
                    )
                )
            )
      }

  private val jobControlTool =
    tool("job_control")
      .description(
        "Pause, resume or cancel a job by `id` (`action`: pause | resume | cancel). A long background job — a picture " +
          "or event import, a base-map download, an acquisition re-check, the init job's gazetteer loads — stops after " +
          "the part it is on: paused it keeps what it did and, resumed, goes on with the parts it had not done; " +
          "cancelled it is recorded as cancelled. An agent run takes its own pause, resume and cancel. Other jobs (an " +
          "OCR, a vacuum) run as one piece and are refused. Only when the user asked for it."
      )
      .input[JobControlInput]
      .handle(in =>
        h.jobControl.control(in.id, in.action).fold(McpOut.error, j => ToolResult.text(IngestMarkdown.job(j)))
      )

  private val activityTool =
    tool("activity")
      .description(
        "Research › Activity: agent runs, background jobs, worklog lines and the person's own decisions in ONE list, " +
          "newest first, each naming what it touched (goal, run or job, source, article, list item, approval). `show`: " +
          "all (default) | running | agents | jobs | yours | problems; `since` (2026-10-01, an ISO instant, or 7d / 12h ago); " +
          "`goal` = one research goal's history; `limit` (default 30, at most 200); `before` = the cursor the last page " +
          "gave, for the next older one. Use to see what happened recently or what is going on now."
      )
      .input[ActivityInput]
      .handle { in =>
        h.activity
          .page(in.show, in.since, in.goal, in.limit, in.before)
          .fold(
            McpOut.error,
            p =>
              ToolResult.text(
                ActivityMarkdown
                  .page(p, c => s"_Older: call `activity` again with the same filters and `before: \"$c\"`._")
              )
          )
      }

  /** Backup is here because an agent asked to "save the state before a big reseed" should be able to do it; RESTORE
    * deliberately is NOT — it drops the database, which is not a decision to leave to a tool call (`histmersion
    * restore` asks a human to confirm).
    */
  /** The config reload (docs/research-plugins.md §3.5): an agent that edited the user's config file on request applies
    * it without a restart.
    */
  private val configTool =
    tool("config")
      .description(
        "The server's own settings, kept in its settings file (never in the database). `get` shows settings " +
          "(`key` one, or `prefix` a block such as `acquire`): what the file says, what applies and from where, " +
          "the default and what the setting does — a credential only as set or not and how it is referred to. " +
          "`set` (`key`, `value` as a person would type it) and `unset` (`key`) change the file in place, keeping " +
          "its comments, order, includes and anything written by hand, check the change first (a refused change " +
          "says why in plain words), keep the old file as .bak and reload the server. A credential is written as " +
          "a reference — env:NAME or <vault>:<entry> — and `value:` only with `literalConsent`. `reload` reads the " +
          "file again and switches the running server to it in place; `restart` restarts a server that runs " +
          "under its service, for the settings only a restart applies (the listening host/port, the bundled " +
          "runtime, pool sizes)."
      )
      .input[ConfigInput]
      .handle { in =>
        def page(f: com.histmersion.api.settings.ConfigEdit.Page => com.histmersion.server.McpTools.PageFResult, prefix: Option[com.histmersion.server.McpTools.PagePrefix]) =
          h.settings.page(prefix).fold(McpOut.error, p => ToolResult.text(f(p)))
        in.action.trim.toLowerCase(java.util.Locale.ROOT) match
          case "reload" => ToolResult.text(ReloadMarkdown.report(h.instance.configReload()))
          case "get"    =>
            in.key.map(_.trim).filter(_.nonEmpty) match
              case Some(k) => page(p => p.field(k).fold(s"No setting called $k.")(ConfigEditMarkdown.field), Some(k))
              case None    => page(ConfigEditMarkdown.page, in.prefix)
          case "set" | "unset" =>
            in.key.map(_.trim).filter(_.nonEmpty) match
              case None    => McpOut.error(Api.Error(s"`${in.action}` needs a `key`"))
              case Some(k) =>
                val value = if in.action.trim.equalsIgnoreCase("unset") then None else Some(in.value.getOrElse(""))
                h.settings
                  .write(
                    com.histmersion.api.settings.ConfigEdit.Write(
                      List(com.histmersion.api.settings.ConfigEdit.Change(k, value)),
                      literalConsent = in.literalConsent.getOrElse(false)
                    )
                  )
                  .fold(McpOut.error, w => ToolResult.text(ConfigEditMarkdown.written(w)))
          case "restart" => h.settings.restart().fold(McpOut.error, r => ToolResult.text(ConfigEditMarkdown.restart(r)))
          case other => McpOut.error(Api.Error(s"unknown action '$other' (one of: get, set, unset, reload, restart)"))
      }

  private val backup =
    tool("backup")
      .description(
        "Take a full pg_dump backup of the Histmersion database, server-side, and return the file name, " +
          "size and SHA-256 checksum. Use it before a bulk reseed, a mass patch, or any other " +
          "operation you may need to undo. Restoring is NOT available as a tool: it destroys data, " +
          "and is done by a human with `histmersion restore <file> --force`."
      )
      .input[BackupInput]
      .handle { _ =>
        h.upkeep.backup() match
          case Right(r) => ToolResult.text(Markdown.backupStatus(r))
          case Left(e)  => McpOut.error(e)
      }

  /** VACUUM (#1445): after a re-seed, a gazetteer load or a place clean-up the place tables hold hundreds of thousands
    * of dead row versions; an agent that just ran one can reclaim them.
    */
  private val vacuumTool =
    tool("vacuum")
      .description(
        "Run PostgreSQL VACUUM — with `analyze: true`, VACUUM (ANALYZE), which also refreshes the planner's " +
          "statistics — over the named `tables`, or over the gazetteer and finding tables a re-seed rewrites " +
          "(config maintenance.vacuum-tables) when none is named. Use it after `places seed`, a gazetteer " +
          "load or a place clean-up (`lint --fix` on the place rules), which leave dead row versions behind. " +
          "It runs as a server job, one table at a time, outside any transaction (no data changes, no table " +
          "lock beyond VACUUM's own); it answers with the job — follow it with `job_status`."
      )
      .input[Vacuum.Request]
      .handle(in => h.upkeep.vacuum(in).fold(McpOut.error, j => ToolResult.text(IngestMarkdown.job(j))))

  // DELETE /api/articles/{slug} is deliberately NOT an MCP tool: dropping compiled knowledge is a
  // human decision, made with `histmersion article delete`, not something a model should reach for while
  // tidying. Every other article operation is on both surfaces.
  // POST /api/sources/{ck}/delete (#969) is deliberately NOT an MCP tool either, for the same reason:
  // removing a source, its findings and its stored original is a person's decision, made with
  // `histmersion delete <citekey> --apply` after reading the dry run.
  private val goalsToolRead =
    tool("goals")
      .description(
        "Research GOALS: the project's research programs and topics. Without `slug` it LISTS goals " +
          "filtered by status (planned|seeding|compiled|reviewed|done); with `slug` it SHOWS one goal with " +
          "its sub-goals, links (sources, articles, queries, queue items), schedules and approved plan."
      )
      .input[GoalsInput]
      .handle { in =>
        in.slug.map(_.trim).filter(_.nonEmpty) match
          case Some(slug) =>
            h.agents.goalView(slug) match
              case Right(v) => ToolResult.text(AgentsMarkdown.goalView(v))
              case Left(e)  => McpOut.error(e)
          case None =>
            val q = Api.GoalQuery(in.status, in.limit, in.offset)
            ToolResult.text(GoalsMarkdown.goalList(h.goals.list(q)))
      }

  private val putGoalTool =
    tool("put_goal")
      .description(
        "Create or replace a research goal. A goal tracks a research program: slug (kebab-case id), " +
          "title, status (planned|seeding|compiled|reviewed|done), notes (progress), tags, regions, " +
          "period."
      )
      .input[GoalPutInput]
      .handle { in =>
        val put =
          Goal.Put(in.title, in.status, in.notes, in.tags, in.regions, in.periodStart, in.periodEnd, in.parentSlug)
        h.goals.put(in.slug, put) match
          case Right(g) =>
            ToolResult.text(Aliases.note(AliasKinds.GoalRecords, in.slug, g.slug)(GoalsMarkdown.goal(g)))
          case Left(e) => McpOut.error(e)
      }

  private val patchGoalTool =
    tool("patch_goal")
      .description("Update specific fields of a goal without replacing the whole row.")
      .input[GoalPatchInput]
      .handle { in =>
        val p = Goal.Patch(
          in.title,
          in.status,
          in.notes,
          in.tags,
          in.regions,
          in.periodStart,
          in.periodEnd,
          in.parentSlug,
          in.budgetUsd
        )
        h.goals.patch(in.slug, p) match
          case Right(g) =>
            ToolResult.text(Aliases.note(AliasKinds.GoalRecords, in.slug, g.slug)(GoalsMarkdown.goal(g)))
          case Left(e) => McpOut.error(e)
      }

  private val agentStateTool =
    tool("agent_state")
      .description(
        "Agent STATE: key-value JSONB store that survives across sessions. Without `key` it LISTS " +
          "all keys; with `key` it SHOWS one entry; with `key` + `value` it SETS (upserts)."
      )
      .input[AgentStateInput]
      .handle { in =>
        in.key.map(_.trim).filter(_.nonEmpty) match
          case Some(key) =>
            in.value.map(_.trim).filter(_.nonEmpty) match
              case Some(v) =>
                h.agentState.set(key, AgentStatePut(v)) match
                  case Right(e) => ToolResult.text(AgentStateMarkdown.agentState(e))
                  case Left(e)  => McpOut.error(e)
              case None =>
                h.agentState.get(key) match
                  case Right(e) => ToolResult.text(AgentStateMarkdown.agentState(e))
                  case Left(e)  => McpOut.error(e)
          case None =>
            ToolResult.text(AgentStateMarkdown.agentStateList(h.agentState.list()))
      }

  private val deleteAgentStateTool =
    tool("delete_agent_state")
      .description("Delete an agent state key.")
      .input[DeleteAgentStateInput]
      .handle { in =>
        h.agentState.delete(in.key) match
          case Right(d) => ToolResult.text(s"Deleted agent state key '${d.key}'.")
          case Left(e)  => McpOut.error(e)
      }

  private val mergePlanTool =
    tool("merge_plan")
      .description(
        "Plan the import of another Histmersion collection's bundle into this one (read-only: it " +
          "decides nothing and writes nothing). Lists EVERY issue in one run — two citekeys for one " +
          "work, one citekey for two works, differing DOIs/years/statuses, records changed on both " +
          "sides, articles that diverged — each with a stable id (`S-3c1e7a2b`), the evidence, and the " +
          "decisions it allows (left = this collection, right = the bundle). `bundle` is the sha256 of " +
          "a bundle uploaded to the server (the user runs `histmersion merge plan <file>`, which " +
          "uploads it and prints the sha256). `resolve` maps issue ids to decisions " +
          "(`keep-left`, `keep-right`, `both`, `map:<citekey>`, …) to see what would remain; `editions` " +
          "is merge|separate. Present the plan to the user; scholarly decisions (statuses, editions, " +
          "articles) are theirs — never resolve them on their behalf."
      )
      .input[MergePlanInput]
      .handle { in =>
        val editions = in.editions.map(_.trim).filter(_.nonEmpty)
        editions.map(e => EditionsPolicy.values.find(_.toString.equalsIgnoreCase(e)).toRight(e)) match
          case Some(Left(e)) => ToolResult.text(s"_editions '$e' is not merge or separate_")
          case other         =>
            val res = MergeResolutions(
              in.resolve.toList.sortBy(_._1).map((k, v) => MergeResolution(k, v)),
              MergePolicy(other.flatMap(_.toOption))
            )
            h.bundles.plan(MergePlan.Request(in.bundle, res)) match
              case Right(p) => ToolResult.text(MergeMarkdown.plan(p, in.all))
              case Left(e)  => McpOut.error(e)
      }

  private val mergesTool =
    tool("merges")
      .description(
        "What this collection took in from other Histmersion collections (read-only). `action`: " +
          "`list` (default) — the merges, newest first, with the bundle, where it came from, the plan " +
          "and the backup taken; `show` with `id` (a merge id or its first characters) — one merge's " +
          "report (sources merged, new, renamed, articles merged three-way…), its resolutions and the " +
          "decisions it remembers; `ledger` — the merge ledger of one record (`uid`: a uid, here or " +
          "merged away, or a citekey) or of one merge (`id`): which record of the other collection " +
          "each one here came from, and the redirects of merged-away uids. Applying a merge is the " +
          "user's (`histmersion merge apply`, or the /merge screen) — never an agent's."
      )
      .input[MergesInput]
      .handle { in =>
        MergesAction.parse(in.action) match
          case None => ToolResult.text(s"_action '${in.action.getOrElse("")}' is not list, show or ledger_")
          case Some(MergesAction.List) => ToolResult.text(MergeMarkdown.merges(h.bundles.merges(in.limit)))
          case Some(MergesAction.Show) =>
            in.id.fold(ToolResult.text("_show needs `id` (a merge id from `list`)_"))(id =>
              h.bundles.mergeDetail(id).fold(McpOut.error, d => ToolResult.text(MergeMarkdown.detail(d)))
            )
          case Some(MergesAction.Ledger) =>
            h.bundles
              .mergeLedger(in.uid, in.id, in.limit)
              .fold(McpOut.error, l => ToolResult.text(MergeMarkdown.ledger(l)))
      }

  private val renameLabel =
    tool("rename_label")
      .description(
        "Give an article a new slug, an event a new id, a goal a new slug, or a gazetteer place a new " +
          "id (`kind`: article | event | goal | place). One transaction: what points at it follows (an " +
          "article's versions, links and every [[old]] in other articles — each rewrite recorded as a " +
          "version; an event's attestations and asset links; a goal's sub-goals, links and schedules; a " +
          "place's names, parents, footprints, events, attestations and finding assignments). " +
          s"${Aliases.Rule} (get_article / events / goals / places show the record under its new one, a " +
          "places filter on the old id still matches.) Refused when the new label " +
          "is taken or is an old label of another record. For a source's citekey use rename_source."
      )
      .input[RenameLabelInput]
      .handle { in =>
        val req = LabelRename(in.to)
        val r = in.kind.trim.toLowerCase(java.util.Locale.ROOT) match
          case "article" => Right(h.research.articles.rename(in.from, req))
          case "event"   => Right(h.events.renamed(in.from, req))
          case "goal"    => Right(h.goals.rename(in.from, req))
          case "place"   => Right(h.places.renamed(in.from, req))
          case other     => Left(s"_kind '$other' is not article, event, goal or place_")
        r.fold(ToolResult.text, _.fold(McpOut.error, x => ToolResult.text(MergeMarkdown.renamed(x))))
      }

  // Declared LAST: every tool above must be initialised before the list is built.
  val server = McpServer(tools =
    List(
      ping
    ) ++ com.histmersion.domain.library.LibraryTools(
      h.library.search,
      h.library.labels,
      h.library.sources,
      h.library.findings,
      h.library.citations,
      ck => h.mirror.note(com.histmersion.domain.library.LibraryData.SourceNotes, ck)
    ) ++ List(
      backup,
      configTool,
      vacuumTool
    ) ++ com.histmersion.domain.research.articles.ArticleTools(h.research.articles) ++
      com.histmersion.domain.catalogue.places.PlacesTools(h.places) ++
      com.histmersion.domain.catalogue.periods.PeriodsTools(h.periods) ++
      com.histmersion.server.views.EventCardTools(h.events, h.eventCard) ++
      com.histmersion.domain.catalogue.evidence.AttestationsTools(h.attestations) ++ List(
        goalsToolRead,
        putGoalTool,
        patchGoalTool,
        agentStateTool,
        deleteAgentStateTool,
        ingest,
        jobStatus,
        jobControlTool,
        activityTool
      ) ++ com.histmersion.domain.media.MediaTools(
        h.media.assets,
        h.media.links,
        ck => h.library.sources.get(ck).isRight
      ) ++ List(
        mergePlanTool,
        mergesTool,
        renameLabel
      )
      ++ OpsTools(h) // stream 2.8: lint · queue · query
      ++ com.histmersion.domain.personal.PersonalTools(h.personal) // the person's bookmarks and notes, read only
      ++ com.histmersion.domain.catalogue.evidence.EventLinksTools(h.eventLinks) // event_links (proposed links, leads)
      ++ com.histmersion.domain.catalogue.events.CorrectionsTools(h.eventImports) // event_corrections
      ++ com.histmersion.domain.automation.agents.AgentsTools( // embedded agents: start and follow runs
        h.agents,
        dry =>
          Right(
            RepairAcquire.markdown(
              com.histmersion.server.agents.EngineContributions
                .work(h.agents)
                .oaBacklog
                .repair(RepairAcquire.RepairRequest(dry, Some("mcp")))
            )
          ),
        st => h.autopilot.page(st).markdown
      )
      // stream 2.6: discover / refs / assess / followups / ingest_doi / archive
      ++ com.histmersion.domain.library.acquisition.AcquisitionTools(acquire, collections).tools
      ++ com.histmersion.domain.library.collections.CollectionsTools(collections) // the Calibre library, archive.org
  )

  val endpoint = server.endpoint(List("mcp"))

  /** Every tool `/mcp` advertises — what a `{{tool:<name>}}` placeholder in a definition may name. */
  def toolNames: Set[com.histmersion.server.McpTools.ToolNamesResult] = server.tools.map(_.name).toSet

object McpTools:
  type PageFResult = PageFResult.T
  object PageFResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PagePrefix = PagePrefix.T
  object PagePrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToolNamesResult = ToolNamesResult.T
  object ToolNamesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  export com.histmersion.util.mcp.ToolInputs.given

  // Circe Codec + tapir Schema both derived from each input by Kindlings → matching by construction.
  final case class PingInput() derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  /** The `config` tool's input: what to do with the server's configuration. */
  final case class ConfigInput(
      action: ConfigInput.Action,
      key: Option[ConfigInput.Key] = None,
      value: Option[ConfigInput.Value] = None,
      prefix: Option[ConfigInput.Prefix] = None,
      literalConsent: Option[ConfigInput.LiteralConsent] = None
  ) derives KindlingsCodecAsObject
  object ConfigInput:
    type Action = Action.T
    object Action extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Prefix = Prefix.T
    object Prefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LiteralConsent = LiteralConsent.T
    object LiteralConsent extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[ConfigInput] = KindlingsSchema
      .derived[ConfigInput]
      .schema
      .modify(_.action)(_.description("get | set | unset | reload | restart"))
      .modify(_.key)(_.description("the setting, e.g. backups.keep (get, set, unset)"))
      .modify(_.value)(_.description("set: the value as a person types it — text, a number, true/false, a list [a, b]"))
      .modify(_.prefix)(_.description("get: the settings under this block, e.g. acquire"))
      .modify(_.literalConsent)(
        _.description("set: a credential may be written as value:… (the key itself in the file)")
      )

  final case class BackupInput() derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  // ── ingest / jobs / assets (plan §1.8). Declared LAST, for the same reason `PlacesInput` was. ──
  final case class IngestInput(
      citekey: IngestInput.Citekey,
      path: Option[IngestInput.Path] = None,
      base64: Option[IngestInput.Base64] = None,
      filename: Option[IngestInput.Filename] = None,
      quality: IngestInput.Quality = false,
      force: IngestInput.Force = false,
      title: Option[IngestInput.Title] = None,
      authors: List[IngestInput.Authors] = Nil,
      year: Option[IngestInput.Year] = None,
      doi: Option[IngestInput.Doi] = None,
      provenance: Option[IngestInput.Provenance] = None,
      acquireItem: Option[IngestInput.AcquireItem] = None
  ) derives KindlingsCodecAsObject
  object IngestInput:
    type Citekey = Citekey.T
    object Citekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Path = Path.T
    object Path extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Base64 = Base64.T
    object Base64 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Filename = Filename.T
    object Filename extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Quality = Quality.T
    object Quality extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Force = Force.T
    object Force extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Year = Year.T
    object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Doi = Doi.T
    object Doi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Provenance = Provenance.T
    object Provenance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AcquireItem = AcquireItem.T
    object AcquireItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[IngestInput] = KindlingsSchema
      .derived[IngestInput]
      .schema
      .modify(_.citekey)(_.description("Citekey to store the source under"))
      .modify(_.path)(_.description("Server-side file path (alternative to base64)"))
      .modify(_.base64)(_.description("Base64-encoded document (alternative to path)"))
      .modify(_.quality)(_.description("Use the slow, accurate OCR path (docling)"))
      .modify(_.force)(_.description("Accept a file already stored under another citekey"))
      .modify(_.provenance)(
        _.description("How the source was acquired: manual|ill|author|scan|external-file|calibre|archive|oa")
      )
      .modify(_.acquireItem)(
        _.description("Id of the acquisition-queue item this document fulfils; ticked off when the OCR job succeeds")
      )

  final case class JobControlInput(id: JobControlInput.Id, action: JobControlInput.Action) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object JobControlInput:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Action = Action.T
    object Action extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ActivityInput(
      show: Option[ActivityInput.Show] = None,
      since: Option[ActivityInput.Since] = None,
      goal: Option[ActivityInput.Goal] = None,
      limit: Option[ActivityInput.Limit] = None,
      before: Option[ActivityInput.Before] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ActivityInput:
    type Show = Show.T
    object Show extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Since = Since.T
    object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Goal = Goal.T
    object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Before = Before.T
    object Before extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class JobStatusInput(
      id: Option[JobStatusInput.Id] = None,
      status: Option[JobStatusInput.Status] = None,
      limit: Option[JobStatusInput.Limit] = None,
      before: Option[JobStatusInput.Before] = None,
      kind: List[JobStatusInput.Kind] = Nil,
      since: Option[JobStatusInput.Since] = None,
      until: Option[JobStatusInput.Until] = None,
      subject: Option[JobStatusInput.Subject] = None
  ) derives KindlingsCodecAsObject:
    def filter: ListFilters.JobFilter = ListFilters.JobFilter(status.toList, kind, since, until, subject)
  object JobStatusInput:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Before = Before.T
    object Before extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Since = Since.T
    object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Until = Until.T
    object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Subject = Subject.T
    object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[JobStatusInput] = KindlingsSchema
      .derived[JobStatusInput]
      .schema
      .modify(_.id)(_.description("Job id to check (omit for a listing)"))
      .modify(_.status)(_.description("Filter listing: queued|running|done|failed (comma-separated for several)"))
      .modify(_.kind)(
        _.description("Filter listing: these job kinds (ingest, reocr, agent, media-import, events-import, init, …)")
      )
      .modify(_.since)(
        _.description("Filter listing: started at or after 2026-10-01, an ISO instant, or 7d / 12h / 30m ago")
      )
      .modify(_.until)(_.description("Filter listing: started before the end of 2026-10-01, or an ISO instant"))
      .modify(_.subject)(
        _.description("Filter listing: text in what the job is about (citekey, target, detail) or the start of its id")
      )
      .modify(_.before)(_.description("Listing: the next older page — the id of the last job shown"))

  final case class GoalsInput(
      slug: Option[GoalsInput.Slug] = None,
      status: Option[GoalsInput.Status] = None,
      limit: Option[GoalsInput.Limit] = None,
      offset: Option[GoalsInput.Offset] = None
  ) derives KindlingsCodecAsObject
  object GoalsInput:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[GoalsInput] = KindlingsSchema
      .derived[GoalsInput]
      .schema
      .modify(_.slug)(_.description("Goal slug to look up directly (omit for listing)"))
      .modify(_.status)(_.description("Filter: planned|seeding|compiled|reviewed|done"))

  final case class GoalPutInput(
      slug: GoalPutInput.Slug,
      title: GoalPutInput.Title,
      status: GoalPutInput.Status = "planned",
      notes: Option[GoalPutInput.Notes] = None,
      tags: List[GoalPutInput.Tags] = Nil,
      regions: List[GoalPutInput.Regions] = Nil,
      periodStart: Option[GoalPutInput.PeriodStart] = None,
      periodEnd: Option[GoalPutInput.PeriodEnd] = None,
      parentSlug: Option[GoalPutInput.ParentSlug] = None
  )
  object GoalPutInput:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Notes = Notes.T
    object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodStart = PeriodStart.T
    object PeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodEnd = PeriodEnd.T
    object PeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParentSlug = ParentSlug.T
    object ParentSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[GoalPutInput] = KindlingsSchema
      .derived[GoalPutInput]
      .schema
      .modify(_.slug)(_.description("Kebab-case identifier for the goal"))
      .modify(_.status)(_.description("planned|seeding|compiled|reviewed|done"))
      .modify(_.periodStart)(_.description("Signed year the goal's period begins (BCE negative); alone = from then on"))
      .modify(_.periodEnd)(_.description("Signed year the goal's period ends (BCE negative); alone = until then"))
    given Codec.AsObject[GoalPutInput] = StrictJson.codec(KindlingsCodecAsObject.derived[GoalPutInput]) // #813

  final case class GoalPatchInput(
      slug: GoalPatchInput.Slug,
      title: Option[GoalPatchInput.Title] = None,
      status: Option[GoalPatchInput.Status] = None,
      notes: Option[GoalPatchInput.Notes] = None,
      tags: Option[List[GoalPatchInput.Tags]] = None,
      regions: Option[List[GoalPatchInput.Regions]] = None,
      periodStart: Option[GoalPatchInput.PeriodStart] = None,
      periodEnd: Option[GoalPatchInput.PeriodEnd] = None,
      parentSlug: Option[GoalPatchInput.ParentSlug] = None,
      budgetUsd: Option[GoalPatchInput.BudgetUsd] = None
  )
  object GoalPatchInput:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Notes = Notes.T
    object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodStart = PeriodStart.T
    object PeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodEnd = PeriodEnd.T
    object PeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParentSlug = ParentSlug.T
    object ParentSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BudgetUsd = BudgetUsd.T
    object BudgetUsd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[GoalPatchInput] = KindlingsSchema
      .derived[GoalPatchInput]
      .schema
      .modify(_.slug)(_.description("Goal slug to patch"))
      .modify(_.periodStart)(
        _.description("New start of the period (signed year, BCE negative); given alone, the end stays as stored")
      )
      .modify(_.periodEnd)(
        _.description("New end of the period (signed year, BCE negative); given alone, the start stays as stored")
      )
      .modify(_.budgetUsd)(_.description("Cost cap (USD) of the agent runs started for this goal; 0 clears it"))
    given Codec.AsObject[GoalPatchInput] = StrictJson.codec(KindlingsCodecAsObject.derived[GoalPatchInput]) // #813

  final case class AgentStateInput(
      key: Option[AgentStateInput.Key] = None,
      value: Option[AgentStateInput.Value] = None
  ) derives KindlingsCodecAsObject
  object AgentStateInput:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[AgentStateInput] = KindlingsSchema
      .derived[AgentStateInput]
      .schema
      .modify(_.key)(_.description("State key to get or set (omit for listing all keys)"))
      .modify(_.value)(_.description("JSON string value to set (omit to just read the key)"))

  final case class DeleteAgentStateInput(key: DeleteAgentStateInput.Key) derives KindlingsCodecAsObject
  object DeleteAgentStateInput:
    type Key = Key.T
    object Key extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[DeleteAgentStateInput] = KindlingsSchema
      .derived[DeleteAgentStateInput]
      .schema
      .modify(_.key)(_.description("State key to delete"))

  /** Input of the `merge_plan` tool (docs/research-merge-import.md §6, M3). */
  final case class MergePlanInput(
      bundle: MergePlanInput.Bundle,
      resolve: Map[MergePlanInput.ResolveKey, MergePlanInput.ResolveValue] = Map.empty,
      editions: Option[MergePlanInput.Editions] = None,
      all: MergePlanInput.All = false
  )
  object MergePlanInput:
    type Bundle = Bundle.T
    object Bundle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveKey = ResolveKey.T
    object ResolveKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResolveValue = ResolveValue.T
    object ResolveValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Editions = Editions.T
    object Editions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[MergePlanInput] = KindlingsSchema
      .derived[MergePlanInput]
      .schema
      .modify(_.bundle)(
        _.description("sha256 of a bundle uploaded to the server (`histmersion merge plan <file>` prints it)")
      )
      .modify(_.resolve)(_.description("Issue id → decision, e.g. {\"S-3c1e7a2b\": \"map:guterbock1997_hittite\"}"))
      .modify(_.editions)(_.description("merge | separate: answers every year-gap (edition) question at once"))
      .modify(_.all)(_.description("List every default, info line and match, not only the first few"))
    given Codec.AsObject[MergePlanInput] = StrictJson.codec(KindlingsCodecAsObject.derived[MergePlanInput])

  /** What the `merges` tool does. */
  enum MergesAction:
    case List, Show, Ledger
  object MergesAction:
    type ParseRaw = ParseRaw.T
    object ParseRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(raw: Option[com.histmersion.server.McpTools.MergesAction.ParseRaw]): Option[MergesAction] =
      raw.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty) match
        case None    => Some(List)
        case Some(a) => values.find(_.toString.equalsIgnoreCase(a))

  /** Input of the `merges` tool (docs/research-merge-import.md §6.1, M7). */
  final case class MergesInput(
      action: Option[MergesInput.Action] = None,
      id: Option[MergesInput.Id] = None,
      uid: Option[MergesInput.Uid] = None,
      limit: Option[MergesInput.Limit] = None
  )
  object MergesInput:
    type Action = Action.T
    object Action extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Uid = Uid.T
    object Uid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[MergesInput] = KindlingsSchema
      .derived[MergesInput]
      .schema
      .modify(_.action)(_.description("list (default) | show | ledger"))
      .modify(_.id)(_.description("A merge id, or its first characters (show; ledger of one merge)"))
      .modify(_.uid)(_.description("ledger: the record — a uid (here, or merged away) or a citekey"))
      .modify(_.limit)(_.description("How many merges / ledger rows (default 50 / 200)"))
    given Codec.AsObject[MergesInput] = StrictJson.codec(KindlingsCodecAsObject.derived[MergesInput])

  /** Input of the `rename_label` tool (V042, V053, V055). */
  final case class RenameLabelInput(kind: RenameLabelInput.Kind, from: RenameLabelInput.From, to: RenameLabelInput.To)
  object RenameLabelInput:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[RenameLabelInput] = KindlingsSchema
      .derived[RenameLabelInput]
      .schema
      .modify(_.kind)(_.description("article | event | goal | place"))
      .modify(_.from)(_.description("The current slug (article, goal) or id (event, place)"))
      .modify(_.to)(_.description("The new one: lower-case letters, digits, '_', '.' and '-'"))
    given Codec.AsObject[RenameLabelInput] = StrictJson.codec(KindlingsCodecAsObject.derived[RenameLabelInput])
