package com.histmersion
package server
import com.histmersion.util.mcp.ChimpJson.given
import com.histmersion.util.log.LogTools
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.api.exchange.EditionsPolicy

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.automation.AgentStateMarkdown
import com.histmersion.api.automation.GoalsMarkdown

import chimp.server.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.{Api, Markdown, Paging, StrictJson}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.util.mcp.{McpConfig, McpEdge, McpOut}
import com.histmersion.api.aliases.{Aliases, LabelRename}
import com.histmersion.api.automation.{ActivityMarkdown, AgentsMarkdown, AgentStatePut, ListFilters}
import com.histmersion.api.exchange.{MergeMarkdown, MergePolicy, MergeResolution, MergeResolutions}
import com.histmersion.api.library.{IngestMarkdown, RepairAcquire}
import com.histmersion.api.settings.{ConfigEditMarkdown, ReloadMarkdown}
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.automation.Goal
import com.histmersion.api.exchange.MergePlan
import com.histmersion.api.maintenance.Vacuum
import com.histmersion.domain.library.acquisition.AcquisitionControllers
import com.histmersion.domain.library.collections.CollectionsControllers
import com.histmersion.api.Write
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.ingest.ToolFiles
import com.histmersion.domain.library.ingest.IngestControllers
import com.histmersion.api.library.Ingest
import com.histmersion.domain.Title
import com.histmersion.domain.library.ingest.IngestMeta
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.acquisition.AcquireAttemptRow
import com.histmersion.util.jobs.Job
import com.histmersion.util.jobs.JobAction
import com.histmersion.api.automation.Activity
import com.histmersion.domain.activity.ActivityQuery
import com.histmersion.domain.activity.ActivityCursor
import com.histmersion.api.settings.ConfigEdit
import com.histmersion.util.settings.SettingsService
import com.histmersion.domain.automation.agents.AgentStateRecord
import com.histmersion.util.data.exchange.MergeRepo
import com.histmersion.api.aliases.LabelRenamed
import com.histmersion.domain.research.Slug
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.library.LibraryTools
import com.histmersion.domain.library.LibraryData
import com.histmersion.util.data.mirror.MirrorPlacement
import com.histmersion.domain.research.articles.ArticleTools
import com.histmersion.domain.catalogue.places.PlacesTools
import com.histmersion.domain.catalogue.periods.PeriodsTools
import com.histmersion.server.views.EventCardTools
import com.histmersion.server.views.OpenWorkTools
import com.histmersion.domain.catalogue.evidence.AttestationsTools
import com.histmersion.domain.media.MediaTools
import com.histmersion.domain.personal.PersonalTools
import com.histmersion.domain.catalogue.evidence.EventLinksTools
import com.histmersion.domain.catalogue.events.CorrectionsTools
import com.histmersion.domain.automation.agents.AgentsTools
import com.histmersion.server.agents.EngineContributions
import com.histmersion.domain.library.acquisition.AcquisitionTools
import com.histmersion.domain.library.collections.CollectionsTools
import com.histmersion.api.automation.Creation
import com.histmersion.domain.CreatorControllers
import com.histmersion.lint.{FeaturePlace, LintRule, movesTo, tolerated}

/** MCP tools (Chimp). Every tool returns Markdown and delegates to the same controllers as REST ([[ServerWiring]]). */
@tolerated(LintRule.Repository, "pending G11 (a repository stays inside its feature)")
@movesTo(FeaturePlace.Server)
final class McpTools(
    h: ServerWiring,
    acquire: AcquisitionControllers,
    collections: CollectionsControllers,
    limits: () => McpConfig
):
  import McpTools.*

  private given Creator.New = Creator.New.Unattributed(Creator.Surface.Mcp)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def refusedValue(what: String, raw: String, code: String): Api.Error =
    Api.Error(Problem.Unclassified(Legacy.Text(s"invalid $what '$raw'")), Some(Api.Error.Code(code)))

  private def invalidValue(what: String, raw: String): Api.Error =
    refusedValue(what, raw, Write.CodeInvalid)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def noSuchJob(raw: String): Api.Error =
    Api.Error(
      Problem.NotFound.Entity(Problem.TypeName.of(Job.Id), Problem.Raw(s"$raw")),
      Some(Api.Error.Code(Write.CodeNotFound))
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val ping =
    tool("ping")
      .description("Liveness check for the Histmersion MCP server")
      .input[PingInput]
      .handle(_ => ToolResult.text(s"${h.instance.version.name} ${h.instance.version.version} — ok"))

  // ── ingest, as a JOB (plan §1.8) ────────────────────────────────────────────────────────────
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
        CreatorControllers.mcp(in) {
          Citekey
            .parse(in.citekey)
            .left
            .map(p => Api.Error(p, Some(Api.Error.Code(Write.CodeInvalid))))
            .flatMap(ck =>
              h.ingest.controllers.fromTool(
                ck,
                in.path.map(ToolFiles.Path(_)),
                in.base64.map(IngestControllers.Base64(_)),
                in.filename.map(Ingest.Params.Filename(_)),
                Ingest.Params.Quality(in.quality),
                Ingest.Params.Force(in.force),
                in.title.map(Title(_)),
                in.authors.map(IngestMeta.Authors(_)),
                in.year.map(Year(_)),
                in.doi.flatMap(Doi.normalize),
                in.acquireItem.map(AcquireAttemptRow.Id(_))
              )
            ) match
            case Right(a) =>
              ToolResult.text(s"queued job `${a.jobId}` for **${a.citekey}** — poll `job_status` with that id")
            case Left(e) => McpOut.error(e)
        }
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
            Job.Id
              .either(id)
              .left
              .map(_ => noSuchJob(id))
              .flatMap(h.jobs.job) match
              case Right(j) => ToolResult.text(IngestMarkdown.job(j))
              case Left(e)  => McpOut.error(e)
          case None =>
            val f = in.filter
            McpOut.of(
              h.jobList
                .page(
                  f,
                  java.time.Instant.now(h.clock),
                  in.limit.flatMap(PageSize.option(_)),
                  in.before.map(Paging.Cursor(_)),
                  None
                )
            )((js, page) =>
              IngestMarkdown.jobs(
                js.toVector,
                page,
                f.qualifier.map(q => Ingest.JobStatus(q.value)),
                Paging
                  .olderLine(
                    page,
                    c => Paging.OlderLine.Text(s"call `job_status` again with the same filters and `before: \"$c\"`")
                  )
                  .map(t => Ingest.JobAge(t.value))
              )
            )
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
        CreatorControllers.mcp(in) {
          Job.Id
            .either(in.id.trim)
            .left
            .map(_ => noSuchJob(in.id))
            .flatMap(h.jobControl.control(_, JobAction.Wire(in.action)))
            .fold(McpOut.error, j => ToolResult.text(IngestMarkdown.job(j)))
        }
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
          .page(
            in.show.map(Activity.Show.Wire(_)),
            in.since.map(ListFilters.TimeBound(_)),
            in.goal.map(ActivityQuery.Goal(_)),
            in.limit.map(ActivityQuery.Limit(_)),
            in.before.map(ActivityCursor.Wire(_))
          )
          .left
          .map(Api.Error(_))
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
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
          "runtime, pool sizes). The settings that decide what agents may fetch (acquire.allow-unauthorised, " +
          "acquire.agents-may-use-unauthorised) are a person's: this tool refuses to change them."
      )
      .input[ConfigInput]
      .handle { in =>
        def page(f: ConfigEdit.Page => String, prefix: Option[String]) =
          h.settings
            .page(prefix.map(SettingsService.ConfigPath(_)))
            .fold(McpOut.error, p => ToolResult.text(f(p)))
        in.action.trim.toLowerCase(java.util.Locale.ROOT) match
          case "reload" => ToolResult.text(ReloadMarkdown.report(h.instance.configReload()))
          case "get"    =>
            in.key.map(_.trim).filter(_.nonEmpty) match
              case Some(k) =>
                page(
                  p =>
                    p.field(ConfigEdit.Field.Key(k))
                      .fold(s"No setting called $k.")(ConfigEditMarkdown.field),
                  Some(k)
                )
              case None => page(ConfigEditMarkdown.page, in.prefix)
          case "set" | "unset" =>
            in.key.map(_.trim).filter(_.nonEmpty) match
              case None => McpOut.error(Api.Error(Problem.Unclassified(Legacy.Text(s"`${in.action}` needs a `key`"))))
              case Some(k) =>
                val value = if in.action.trim.equalsIgnoreCase("unset") then None else Some(in.value.getOrElse(""))
                h.settings
                  .writeByAgent(
                    ConfigEdit.Write(
                      List(
                        ConfigEdit.Change(
                          ConfigEdit.Field.Key(k),
                          value.map(ConfigEdit.Change.Value(_))
                        )
                      ),
                      literalConsent = ConfigEdit.Write.LiteralConsent(in.literalConsent.getOrElse(false))
                    )
                  )
                  .fold(McpOut.error, w => ToolResult.text(ConfigEditMarkdown.written(w)))
          case "restart" => h.settings.restart().fold(McpOut.error, r => ToolResult.text(ConfigEditMarkdown.restart(r)))
          case other     =>
            McpOut.error(
              Api.Error(
                Problem.Unclassified(Legacy.Text(s"unknown action '$other' (one of: get, set, unset, reload, restart)"))
              )
            )
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
            domain.automation.Goal.Slug
              .either(slug)
              .left
              .map(_ => refusedValue("goal", slug, Write.CodeNotFound))
              .flatMap(h.agents.goalView) match
              case Right(v) => ToolResult.text(AgentsMarkdown.goalView(v))
              case Left(e)  => McpOut.error(e)
          case None =>
            val q = Api.GoalQuery(
              in.status.map(Api.GoalQuery.Status(_)),
              in.limit.map(Api.GoalQuery.Limit(_)),
              in.offset.map(Api.GoalQuery.Offset(_))
            )
            ToolResult.text(GoalsMarkdown.goalList(h.goals.list(q)))
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private val putGoalTool =
    tool("put_goal")
      .description(
        "Create or replace a research goal. A goal tracks a research program: slug (kebab-case id), " +
          "title, status (planned|seeding|compiled|reviewed|done), notes (progress), tags, regions, " +
          "period."
      )
      .input[GoalPutInput]
      .handle { in =>
        CreatorControllers.mcp(in) {
          val put =
            Goal.Put(
              Goal.Title(in.title),
              Goal.StatusValue(in.status),
              in.notes.map(Goal.Notes(_)),
              in.tags.map(Goal.Tags(_)),
              in.regions.map(Goal.Regions(_)),
              in.periodStart.map(com.histmersion.api.catalogue.Year(_)),
              in.periodEnd.map(com.histmersion.api.catalogue.Year(_)),
              in.parentSlug.map(Goal.Slug(_))
            )
          domain.automation.Goal.Slug
            .either(in.slug)
            .left
            .map(_ => invalidValue("goal slug", in.slug))
            .flatMap(h.goals.put(_, put)) match
            case Right(g) =>
              ToolResult.text(
                Aliases
                  .note(AliasKinds.GoalRecords, Aliases.Id(in.slug), Aliases.Id(g.slug.value))(
                    Aliases.Note(GoalsMarkdown.goal(g))
                  )
                  .value
              )
            case Left(e) => McpOut.error(e)
        }
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private val patchGoalTool =
    tool("patch_goal")
      .description("Update specific fields of a goal without replacing the whole row.")
      .input[GoalPatchInput]
      .handle { in =>
        val p = Goal.Patch(
          in.title.map(Goal.Title(_)),
          in.status.map(Goal.StatusValue(_)),
          in.notes.map(Goal.Notes(_)),
          in.tags.map(_.map(Goal.Tags(_))),
          in.regions.map(_.map(Goal.Regions(_))),
          in.periodStart.map(com.histmersion.api.catalogue.Year(_)),
          in.periodEnd.map(com.histmersion.api.catalogue.Year(_)),
          in.parentSlug.map(Goal.Slug(_)),
          in.budgetUsd.map(Goal.Patch.BudgetUsd(_))
        )
        domain.automation.Goal.Slug
          .either(in.slug)
          .left
          .map(_ => invalidValue("goal slug", in.slug))
          .flatMap(h.goals.patch(_, p)) match
          case Right(g) =>
            ToolResult.text(
              Aliases
                .note(AliasKinds.GoalRecords, Aliases.Id(in.slug), Aliases.Id(g.slug.value))(
                  Aliases.Note(GoalsMarkdown.goal(g))
                )
                .value
            )
          case Left(e) => McpOut.error(e)
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
                h.agentState.set(
                  AgentStateRecord.Key(key),
                  AgentStatePut(AgentStatePut.Value(v))
                ) match
                  case Right(e) => ToolResult.text(AgentStateMarkdown.agentState(e))
                  case Left(e)  => McpOut.error(e)
              case None =>
                h.agentState.get(AgentStateRecord.Key(key)) match
                  case Right(e) => ToolResult.text(AgentStateMarkdown.agentState(e))
                  case Left(e)  => McpOut.error(e)
          case None =>
            ToolResult.text(AgentStateMarkdown.agentStateList(h.agentState.list()))
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val deleteAgentStateTool =
    tool("delete_agent_state")
      .description("Delete an agent state key.")
      .input[DeleteAgentStateInput]
      .handle { in =>
        h.agentState.delete(AgentStateRecord.Key(in.key)) match
          case Right(d) => ToolResult.text(s"Deleted agent state key '${d.key}'.")
          case Left(e)  => McpOut.error(e)
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
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
          case Some(Left(e)) =>
            McpOut.unknown(Problem.TypeName("EditionsPolicy"), e, Problem.Raw("merge"), Problem.Raw("separate"))
          case other =>
            val res = MergeResolutions(
              in.resolve.toList
                .sortBy(_._1)
                .map((k, v) => MergeResolution(MergeResolution.Issue(k), MergeResolution.Decision(v))),
              MergePolicy(other.flatMap(_.toOption))
            )
            h.bundles.plan(MergePlan.Request(MergePlan.Request.Bundle(in.bundle), res)) match
              case Right(p) => ToolResult.text(MergeMarkdown.plan(p, in.all))
              case Left(e)  => McpOut.error(e)
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
          case None =>
            McpOut.unknown(
              Problem.TypeName("MergesAction"),
              in.action.getOrElse(""),
              Problem.Raw("list"),
              Problem.Raw("show"),
              Problem.Raw("ledger")
            )
          case Some(MergesAction.List) =>
            ToolResult.text(MergeMarkdown.merges(h.bundles.merges(in.limit.map(Api.Merge.Limit(_)))))
          case Some(MergesAction.Show) =>
            in.id.fold(
              McpOut.error(Api.Error(Problem.Invalid.Missing.here(Problem.Field("id"))), "A merge id from `list`.")
            )(id =>
              h.bundles
                .mergeDetail(MergeRepo.MergeIdOrPrefix(id))
                .fold(McpOut.error, d => ToolResult.text(MergeMarkdown.detail(d)))
            )
          case Some(MergesAction.Ledger) =>
            h.bundles
              .mergeLedger(
                in.uid.map(MergeRepo.UidText(_)),
                in.id.map(MergeRepo.MergeIdOrPrefix(_)),
                in.limit.map(Api.Merge.Limit(_))
              )
              .fold(McpOut.error, l => ToolResult.text(MergeMarkdown.ledger(l)))
      }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
        val req = LabelRename(LabelRename.To(in.to))
        def renamed[A](parsed: Either[String, A], what: String)(rename: A => Either[Api.Error, LabelRenamed]) =
          Right(parsed.left.map(_ => invalidValue(what, in.from)).flatMap(rename))
        val r = in.kind.trim.toLowerCase(java.util.Locale.ROOT) match
          case "article" =>
            renamed(Slug.parse(in.from).left.map(_.sentence), "slug")(
              h.research.articles.rename(_, req)
            )
          case "event" =>
            renamed(Event.Id.either(in.from.trim), "event id")(h.events.renamed(_, req))
          case "goal" =>
            renamed(domain.automation.Goal.Slug.either(in.from.trim), "goal slug")(
              h.goals.rename(_, req)
            )
          case "place" =>
            renamed(Place.Id.either(in.from.trim), "place id")(h.places.renamed(_, req))
          case other =>
            Left(
              McpOut.unknown(
                Problem.TypeName("LabelKind"),
                other,
                Problem.Raw("article"),
                Problem.Raw("event"),
                Problem.Raw("goal"),
                Problem.Raw("place")
              )
            )
        r.fold(identity, McpOut.of(_)(MergeMarkdown.renamed))
      }

  // Declared LAST: every tool above must be initialised before the list is built.
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val server = McpEdge.server(
    limits = limits,
    tools = List(
      ping
    ) ++ LibraryTools(
      h.library.search,
      h.library.labels,
      h.library.sources,
      h.library.findings,
      h.library.citations,
      ck => h.mirror.note(LibraryData.SourceNotes, MirrorPlacement.RecordId(ck.value))
    ) ++ List(
      backup,
      configTool,
      vacuumTool
    ) ++ ArticleTools(h.research.articles) ++
      PlacesTools(h.places) ++
      PeriodsTools(h.periods) ++
      EventCardTools(h.events, h.eventCard) ++
      AttestationsTools(h.attestations) ++ List(
        goalsToolRead,
        putGoalTool,
        patchGoalTool,
        agentStateTool,
        deleteAgentStateTool,
        ingest,
        jobStatus,
        jobControlTool,
        activityTool
      ) ++ LogTools(h.log).tools ++ MediaTools(
        h.media.assets,
        h.media.links,
        ck => h.library.sources.get(ck).left.map(Api.Error(_)).isRight
      ) ++ List(
        mergePlanTool,
        mergesTool,
        renameLabel
      )
      ++ OpsTools(h) // stream 2.8: lint · queue · query
      ++ PersonalTools(h.personal) // the person's bookmarks and notes, read only
      ++ EventLinksTools(h.eventLinks) // event_links (proposed links, leads)
      ++ CorrectionsTools(h.eventImports) // event_corrections
      ++ AgentsTools( // embedded agents: start and follow runs
        h.agents,
        dry =>
          Right(
            RepairAcquire
              .markdown(
                h.maintenance.writing(
                  EngineContributions
                    .work(h.agents)
                    .oaBacklog
                    .repair(
                      RepairAcquire
                        .RepairRequest(RepairAcquire.RepairRequest.DryRun(dry.value), Some(Activity.Actor("mcp")))
                    )
                )
              )
              .value
          ),
        st => h.autopilot.page(st).markdown
      )
      // stream 2.6: discover / refs / assess / followups / ingest_doi / archive
      ++ AcquisitionTools(acquire, collections).tools
      ++ CollectionsTools(collections) // the Calibre library, archive.org
      ++ OpenWorkTools(h.researchState).tools
  )

  val endpoint = server.endpoint(List("mcp"))

  /** Every tool `/mcp` advertises — what a `{{tool:<name>}}` placeholder in a definition may name. */
  def toolNames: Set[String] = server.tools.map(_.name).toSet

object McpTools:
  export util.mcp.ToolInputs.given

  // JSON codec + tapir Schema both derived from each input by Kindlings → matching by construction.
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class PingInput() derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

  /** The `config` tool's input: what to do with the server's configuration. */
  final case class ConfigInput(
      action: String,
      key: Option[String] = None,
      value: Option[String] = None,
      prefix: Option[String] = None,
      literalConsent: Option[Boolean] = None
  ) derives JsonCodec
  object ConfigInput:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
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

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class BackupInput() derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

  // ── ingest / jobs / assets (plan §1.8). Declared LAST, for the same reason `PlacesInput` was. ──
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  final case class IngestInput(
      citekey: String,
      path: Option[String] = None,
      base64: Option[String] = None,
      filename: Option[String] = None,
      quality: Boolean = false,
      force: Boolean = false,
      title: Option[String] = None,
      authors: List[String] = Nil,
      year: Option[Int] = None,
      doi: Option[String] = None,
      provenance: Option[String] = None,
      acquireItem: Option[Int] = None,
      createdBy: Option[Creation.By] = None
  ) extends Creation.Named derives JsonCodec
  object IngestInput:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
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

  final case class JobControlInput(id: String, action: String, createdBy: Option[Creation.By] = None)
      extends Creation.Named derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  final case class ActivityInput(
      show: Option[String] = None,
      since: Option[String] = None,
      goal: Option[String] = None,
      limit: Option[Int] = None,
      before: Option[String] = None
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  final case class JobStatusInput(
      id: Option[String] = None,
      status: Option[String] = None,
      limit: Option[Int] = None,
      before: Option[String] = None,
      kind: List[String] = Nil,
      since: Option[String] = None,
      until: Option[String] = None,
      subject: Option[String] = None
  ) derives JsonCodec:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def filter: ListFilters.JobFilter =
      ListFilters.JobFilter(
        status.toList.map(ListFilters.JobFilter.Status(_)),
        kind.map(ListFilters.JobFilter.Kind(_)),
        since.map(ListFilters.JobFilter.Since(_)),
        until.map(ListFilters.JobFilter.Until(_)),
        subject.map(ListFilters.JobFilter.Subject(_))
      )
  object JobStatusInput:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
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
      slug: Option[String] = None,
      status: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives JsonCodec
  object GoalsInput:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[GoalsInput] = KindlingsSchema
      .derived[GoalsInput]
      .schema
      .modify(_.slug)(_.description("Goal slug to look up directly (omit for listing)"))
      .modify(_.status)(_.description("Filter: planned|seeding|compiled|reviewed|done"))

  final case class GoalPutInput(
      slug: String,
      title: String,
      status: String = "planned",
      notes: Option[String] = None,
      tags: List[String] = Nil,
      regions: List[String] = Nil,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      parentSlug: Option[String] = None,
      createdBy: Option[Creation.By] = None
  ) extends Creation.Named derives StrictJson
  object GoalPutInput:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[GoalPutInput] = KindlingsSchema
      .derived[GoalPutInput]
      .schema
      .modify(_.slug)(_.description("Kebab-case identifier for the goal"))
      .modify(_.status)(_.description("planned|seeding|compiled|reviewed|done"))
      .modify(_.periodStart)(_.description("Signed year the goal's period begins (BCE negative); alone = from then on"))
      .modify(_.periodEnd)(_.description("Signed year the goal's period ends (BCE negative); alone = until then"))

  final case class GoalPatchInput(
      slug: String,
      title: Option[String] = None,
      status: Option[String] = None,
      notes: Option[String] = None,
      tags: Option[List[String]] = None,
      regions: Option[List[String]] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      parentSlug: Option[String] = None,
      budgetUsd: Option[Double] = None
  ) derives StrictJson
  object GoalPatchInput:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
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

  final case class AgentStateInput(
      key: Option[String] = None,
      value: Option[String] = None
  ) derives JsonCodec
  object AgentStateInput:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[AgentStateInput] = KindlingsSchema
      .derived[AgentStateInput]
      .schema
      .modify(_.key)(_.description("State key to get or set (omit for listing all keys)"))
      .modify(_.value)(_.description("JSON string value to set (omit to just read the key)"))

  final case class DeleteAgentStateInput(key: String) derives JsonCodec
  object DeleteAgentStateInput:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[DeleteAgentStateInput] = KindlingsSchema
      .derived[DeleteAgentStateInput]
      .schema
      .modify(_.key)(_.description("State key to delete"))

  /** Input of the `merge_plan` tool (docs/research-merge-import.md §6, M3). */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  final case class MergePlanInput(
      bundle: String,
      resolve: Map[String, String] = Map.empty,
      editions: Option[String] = None,
      all: Boolean = false
  ) derives StrictJson
  object MergePlanInput:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[MergePlanInput] = KindlingsSchema
      .derived[MergePlanInput]
      .schema
      .modify(_.bundle)(
        _.description("sha256 of a bundle uploaded to the server (`histmersion merge plan <file>` prints it)")
      )
      .modify(_.resolve)(_.description("Issue id → decision, e.g. {\"S-3c1e7a2b\": \"map:guterbock1997_hittite\"}"))
      .modify(_.editions)(_.description("merge | separate: answers every year-gap (edition) question at once"))
      .modify(_.all)(_.description("List every default, info line and match, not only the first few"))

  /** What the `merges` tool does. */
  enum MergesAction:
    case List, Show, Ledger
  object MergesAction:
    def parse(raw: Option[String]): Option[MergesAction] =
      raw.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty) match
        case None    => Some(List)
        case Some(a) => values.find(_.toString.equalsIgnoreCase(a))

  /** Input of the `merges` tool (docs/research-merge-import.md §6.1, M7). */
  final case class MergesInput(
      action: Option[String] = None,
      id: Option[String] = None,
      uid: Option[String] = None,
      limit: Option[Int] = None
  ) derives StrictJson
  object MergesInput:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[MergesInput] = KindlingsSchema
      .derived[MergesInput]
      .schema
      .modify(_.action)(_.description("list (default) | show | ledger"))
      .modify(_.id)(_.description("A merge id, or its first characters (show; ledger of one merge)"))
      .modify(_.uid)(_.description("ledger: the record — a uid (here, or merged away) or a citekey"))
      .modify(_.limit)(_.description("How many merges / ledger rows (default 50 / 200)"))

  /** Input of the `rename_label` tool (V042, V053, V055). */
  final case class RenameLabelInput(kind: String, from: String, to: String) derives StrictJson
  object RenameLabelInput:
    @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
    @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
    given Schema[RenameLabelInput] = KindlingsSchema
      .derived[RenameLabelInput]
      .schema
      .modify(_.kind)(_.description("article | event | goal | place"))
      .modify(_.from)(_.description("The current slug (article, goal) or id (event, place)"))
      .modify(_.to)(_.description("The new one: lower-case letters, digits, '_', '.' and '-'"))
