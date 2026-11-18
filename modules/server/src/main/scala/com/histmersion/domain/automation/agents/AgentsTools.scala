package com.histmersion
package domain.automation.agents
import com.histmersion.util.mcp.ChimpJson
import com.histmersion.util.mcp.ChimpJson.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.Cursor
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.Text.sentence

import com.histmersion.domain.automation.workflows.Pipelines

import chimp.server.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.PageSize
import com.histmersion.domain.automation.managers.{ManagerContext, ManagerLight}
import com.histmersion.api.Paging
import com.histmersion.domain.automation.Approval as DomainApproval
import com.histmersion.domain.automation.goals.BGoalLinkRow
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.{DecisionRequest, RunRequest}
import com.histmersion.util.mcp.McpOut
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.api.automation.{
  AgentMode,
  Agents,
  AgentScheduler,
  AgentSchedulerMarkdown,
  AgentsMarkdown,
  ApprovalDigest,
  ApprovalDigestMarkdown,
  Autopilot,
  AutopilotMarkdown,
  ListFilters,
  Managers,
  ManagersMarkdown,
  ModelChains,
  PlanQuota,
  StallsMarkdown
}
import com.histmersion.api.Api
import com.histmersion.api.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.ScheduleKind
import com.histmersion.api.automation.AgentRole
import com.histmersion.domain.automation.managers.ManagersControllers

/** The MCP half of the embedded agents: an external session can hand a whole pipeline to the server with ONE call
  * (`start_run` — e.g. the whole review panel of an article, instead of spawning four subagents), check on it
  * (`run_status`), and see and decide what runs proposed (`list_approvals`, `decide_approval`). Same rule as every
  * tool: a thin wrapper over [[AgentsControllers]], Markdown out.
  */
object AgentsTools:
  type RepairAcquire = RepairAcquire.T
  object RepairAcquire extends Newtype[Boolean, Pure]

  import com.histmersion.util.mcp.ToolInputs.given

  /** `repairAcquire`: the acquisition list's one-shot repair (dry run or not), as Markdown — the library's, bound by
    * the composition.
    */
  def apply(
      a: AgentsControllers,
      repairAcquire: RepairAcquire => Either[Api.Error, String],
      autopilotPage: Autopilot.EngineStatus => String
  ) =
    val start =
      tool("start_run")
        .description(
          "Start an embedded agent pipeline on the SERVER (off unless the server has agents enabled). " +
            s"Pipelines: ${Agents.Pipeline.values.map(p => s"`${p.wire}` <${p.targetKind.wire}> — ${p.describe}").mkString("; ")}. " +
            "Returns the run id at once; the run is checkpointed, budgeted and resumable. Check it with " +
            "`run_status`. Use instead of doing the same steps yourself when the server's agents are on."
        )
        .input[RunInput]
        .handle { in =>
          in.modelFor.map(m => Agents.ModelOverride.parse(Agents.ModelOverride.Wire(m))).partitionMap(identity) match
            case (Nil, over) =>
              a.startRun(
                RunRequest(
                  RunRequest.Pipeline(in.pipeline),
                  RunRequest.Target(in.target),
                  in.model.map(RunRequest.Model(_)),
                  in.budgetUsd.map(RunRequest.BudgetUsd(_)),
                  None,
                  Option.when(in.fix)(RunRequest.Fix(true)),
                  in.rounds.map(RunRequest.Rounds(_)),
                  in.acquireItem.map(Queue.QueueItem.Id(_)),
                  in.title.map(RunRequest.Title(_)),
                  in.kind.map(RunRequest.Kind(_)),
                  in.query.map(RunRequest.Query(_)),
                  in.topics.map(RunRequest.Topics(_)),
                  in.tags.map(RunRequest.Tags(_)),
                  in.goal.map(Goal.Slug(_)),
                  Option.when(in.dryRun)(RunRequest.DryRun(true)),
                  over
                )
              ) match
                case Right(r) => ToolResult.text(AgentsMarkdown.runLine(r))
                case Left(e)  => McpOut.error(e)
            case (errs, _) => McpOut.text(McpOut.refusal(errs.map(_.sentence).mkString("; ")))
        }

    val status =
      tool("run_status")
        .description(
          "Embedded agent runs: with `id`, one run's steps, tokens, cost, warnings (and its trace with " +
            "`messages: true`); with `compare` (two or more run ids of one pipeline on one target), the runs side " +
            "by side — per step the model (configured → reported), tokens, cost or plan pool, time, attempts, then " +
            "the outputs; without either, the newest runs, filtered on the server by `status` (" +
            s"${Agents.RunState.values.map(_.wire).mkString(", ")} — `waiting` = for an approval), `pipeline`, `origin`, " +
            "the process `manager` that dispatched them, one `goal`, the `model` or `provider` a step used, `since` / " +
            "`until` (2026-10-01, an ISO instant, or 7d / 12h ago) and `subject` (text in the target); each listing ends with " +
            "the counts per pipeline, origin, manager, provider and model."
        )
        .input[RunsInput]
        .handle { in =>
          in.id.map(_.trim).filter(_.nonEmpty) match
            case _ if in.compare.nonEmpty =>
              ToolResult.text(
                AgentsControllers.runIds(in.compare).flatMap(a.compare).fold(McpOut.refusal, AgentsMarkdown.compare)
              )
            case Some(id) =>
              ToolResult.text(
                AgentsControllers.runId(id).flatMap(a.run).fold(McpOut.refusal, AgentsMarkdown.run(_, in.messages))
              )
            case None =>
              val f = in.filter
              ToolResult.text(
                a.runsPage(f, in.limit.flatMap(PageSize.option(_)), in.before.map(Paging.Cursor(_)), None)
                  .fold(
                    McpOut.refusal,
                    (rs, page) =>
                      AgentsMarkdown.runs(
                        rs,
                        page,
                        f.qualifier.map(q => Agents.RunInfo.Status(q.value)),
                        Paging
                          .olderLine(
                            page,
                            c =>
                              Paging.OlderLine
                                .Text(s"call `run_status` again with the same filters and `before: \"$c\"`")
                          )
                          .map(t => Paging.Cursor(t.value))
                      )
                  )
              )
        }

    val approvals =
      tool("list_approvals")
        .description(
          "Changes embedded agent runs PROPOSED and a person decides: a source's status, or a disagreement " +
            "edge (challenges, refutes, updates, superseded_by), each with the evidence and the verified " +
            "quote it rests on; a goal's plan (sub-goals, queries, works) or an article a run compiled, " +
            "which the run WAITS for. Default: the open ones; `state` = open, approved, rejected, failed or all; " +
            s"`kind` = any of ${Agents.ApprovalKind.values.map(_.wire).mkString(", ")}; `subject` = text in what it is " +
            "about (a slug, a manager, a citekey); `goal` = one goal's; `decidedBy` = person, autopilot or auto. The " +
            "listing heads with the counts per kind and per decider. `digest: true` = the digest instead: everything waiting " +
            "for a person by kind (how many, the oldest, who decides them now, the subjects, the ids) and the manager items " +
            "held for a person."
        )
        .input[ApprovalsInput]
        .handle { in =>
          val f = in.filter
          if in.digest then ToolResult.text(ApprovalDigestMarkdown.digest(a.managing.digest))
          else
            ToolResult.text(
              a.approvalsPage(f, in.limit.flatMap(PageSize.option(_)), in.before.map(Paging.Cursor(_)), None)
                .fold(
                  McpOut.refusal,
                  (as, page) =>
                    AgentsMarkdown.approvals(
                      as,
                      page,
                      f.qualifier.map(q => Approval.State.Wire(q.value)),
                      Paging
                        .olderLine(
                          page,
                          c =>
                            Paging.OlderLine
                              .Text(s"call `list_approvals` again with the same filters and `before: \"$c\"`")
                        )
                        .map(t => Paging.Cursor(t.value))
                    )
                )
            )
        }

    val decide =
      tool("decide_approval")
        .description(
          "Approve or reject one open approval by `id`. Approving APPLIES the change through the ordinary " +
            "source patch (status or edge), or lets the waiting run apply its plan or write its article; " +
            "rejecting keeps it on record. Without `id`, a whole group at once: every open approval of `kind`, about " +
            "`subject`, or in `ids` (they narrow together), the oldest first, at most `limit`; `dryRun: true` lists " +
            "what it would decide. Decide only what the user asked you to, or what you have verified against the " +
            "record yourself."
        )
        .input[DecideInput]
        .handle { in =>
          in.id match
            case Some(id) =>
              ToolResult.text(
                a.decide(
                  DomainApproval.Id(id),
                  DomainApproval.Outcome(in.decision),
                  DecisionRequest(in.note.map(DecisionRequest.Note(_)))
                ).fold(McpOut.refusal, x => AgentsMarkdown.approvalLine(x))
              )
            case None =>
              ToolResult.text(
                a.managing
                  .decideAll(
                    ApprovalDigest
                      .BulkDecide(
                        ApprovalDigest.BulkDecide.Decision(in.decision),
                        in.kind.map(ApprovalDigest.BulkDecide.Kind(_)),
                        in.subject.map(ApprovalDigest.BulkDecide.Subject(_)),
                        in.ids.map(ApprovalDigest.Open.Id(_)),
                        in.note.map(ApprovalDigest.BulkDecide.Note(_)),
                        ApprovalDigest.BulkDecide.DryRun(in.dryRun),
                        in.limit.map(ApprovalDigest.BulkDecide.Limit(_)),
                        Some(ApprovalDigest.BulkDecide.By("mcp"))
                      )
                  )
                  .fold(McpOut.refusal, ApprovalDigestMarkdown.bulk)
              )
        }

    val links =
      tool("goal_links")
        .description(
          "Link a research goal to what it is about, or unlink it: a source (citekey), an article (slug), a " +
            "literature search query (what the goal's acquisition sweep searches for), or a queue item (id). " +
            "`remove: true` unlinks. Answers the goal with all its links and schedules."
        )
        .input[GoalLinkInput]
        .handle { in =>
          val kind = Goal.Plans.LinkKind.parse(Goal.Plans.LinkKind.Wire(in.kind))
          val done =
            kind
              .toRight(
                Api.Error(
                  Problem.Invalid.Unknown.here(
                    Problem.TypeName.of(Goal.Plans.LinkKind.Wire),
                    Problem.Raw(in.kind),
                    Goal.Plans.LinkKind.values.toVector.map(v => Problem.Raw.of(v.wire))
                  )
                )
              )
              .flatMap { k =>
                AgentsControllers.goalSlug(in.goal).flatMap { g =>
                  if in.remove then a.removeGoalLink(g, k.wire, BGoalLinkRow.Ref(in.ref)).map(_ => ())
                  else
                    a.addGoalLink(
                      g,
                      Goal.Plans.LinkAdd(k, Goal.Plans.LinkAdd.Ref(in.ref), in.note.map(Goal.Plans.LinkAdd.Note(_)))
                    ).map(_ => ())
                }
              }
          ToolResult.text(
            done
              .flatMap(_ => AgentsControllers.goalSlug(in.goal).flatMap(a.goalView))
              .fold(McpOut.refusal, AgentsMarkdown.goalView)
          )
        }

    val schedule =
      tool("goal_schedule")
        .description(
          "A research goal's schedules — the sweeps of its watcher manager `watch:<goal>` (created PAUSED: a person " +
            "resumes it with the `manager` tool or the Managers window; it runs only while the server's agents are enabled): " +
            "`currency` (assess + followups over the goal's sources; disagreement edges proposed for approval) and " +
            "`acquisition` (search the goal's queries; a model picks what to add to the acquisition list). " +
            "With `cron` (five fields in UTC, or @daily/@weekly/@monthly; the watcher's one cron, shared by both) sets one; " +
            "`remove: true` takes it out of the watcher; with neither, lists every schedule. The daily/monthly cost caps " +
            "are checked before each start."
        )
        .input[ScheduleInput]
        .handle { in =>
          val out = (in.goal, in.kind) match
            case (Some(g), Some(k)) if in.remove =>
              AgentsControllers
                .goalSlug(g)
                .flatMap(s => a.removeSchedule(s, ScheduleKind.Wire(k)))
                .map(_ => AgentsMarkdown.schedules(a.schedules))
            case (Some(g), Some(k)) =>
              in.cron
                .toRight(
                  Api.Error(Problem.Invalid.Broken.here(Problem.Invalid.Rule.CronGiven))
                )
                .flatMap(c =>
                  AgentsControllers
                    .goalSlug(g)
                    .flatMap(slug =>
                      a.setSchedule(
                        slug,
                        ScheduleKind.Wire(k),
                        Goal.Plans.ScheduleSet(
                          Goal.Plans.ScheduleSet.Cron(c),
                          Goal.Plans.ScheduleSet.Enabled(in.enabled),
                          in.budgetUsd.map(Goal.Plans.ScheduleSet.BudgetUsd(_))
                        )
                      )
                    )
                    .map(s => AgentsMarkdown.scheduleLine(s))
                )
            case (None, None) => Right(AgentsMarkdown.schedules(a.schedules))
            case _            =>
              Left(
                Api.Error(
                  Problem.Invalid.Inconsistent
                    .here(
                      NonEmptyVector.of(Problem.Field("goal"), Problem.Field("kind")),
                      Problem.Invalid.Exclusion.Together
                    )
                )
              )
          ToolResult.text(out.fold(McpOut.refusal, identity))
        }

    val models =
      tool("agent_models")
        .description(
          "The server's embedded-agent models: each step role's CHAIN (models tried in order; the first with " +
            "allowance left is used), and each allowance (tokens/USD per rolling window, a reserve) with what " +
            "was used, what is left and when a window frees. `quota: true` instead shows the subscription pools " +
            "(Claude plan, ChatGPT plan): each window's use against its floor, its reset, how old the reading is, " +
            "the hold switch. `doctor: true` checks every provider without spending anything (keys, the free " +
            "model listing, prices, the claude/codex/opencode CLIs and their logins)."
        )
        .input[ModelsInput]
        .handle { in =>
          ToolResult.text(if in.doctor then
            AgentsMarkdown.doctor(a.doctor(offline = AgentsControllers.Offline(in.offline)))
          else if in.quota then AgentsMarkdown.quota(a.quota)
          else AgentsMarkdown.models(a.models))
        }

    val setModels =
      tool("set_agent_models")
        .description(
          "Change the embedded agents' models (saved in the database over the server's config): with `role` " +
            "and `models`, that role's chain (provider:model, first choice first, e.g. claude-code:sonnet, " +
            "anthropic:<model>, local:<model>); with `target` (provider:model, or provider:* for every model of " +
            "a provider) and `limits` (e.g. \"2M tokens/5h\", \"5 usd/day\") plus `reservePct`, that allowance. " +
            "`reset: true` drops the saved value (config applies again). With `pool` (claude-plan, chatgpt-plan): " +
            "`hold` true/false pauses or releases all agent use of that subscription, `floors` (\"five_hour=30, " +
            "seven_day=25\"; \"\" = config's) the share of each window that must stay left; `refresh: true` " +
            "takes a fresh reading now (without `pool`: every pool; the Claude plan's costs one minimal model turn). With `role` and " +
            "`fallthrough`: whether that chain may fall from a plan below its floor to a paid key. Change only " +
            "what the user asked for."
        )
        .input[SetModelsInput]
        .handle { in =>
          val out = (in.role.map(_.trim).filter(_.nonEmpty), in.target.map(_.trim).filter(_.nonEmpty)) match
            case _ if in.refresh =>
              a.refreshQuota(PlanQuota.QuotaRefresh(in.pool.map(PlanQuota.QuotaRefresh.Pool(_))))
                .left
                .map(Api.Error(_))
                .map(AgentsMarkdown.refreshed)
            case _ if in.pool.exists(_.trim.nonEmpty) =>
              if in.hold.isEmpty && in.floors.isEmpty then
                Left(
                  Api.Error(
                    Problem.Invalid.Inconsistent(
                      NonEmptyVector.of(Problem.Field("hold"), Problem.Field("floors")),
                      Problem.Invalid.Exclusion.AtLeastOne,
                      Vector(Problem.Field("pool"))
                    )
                  )
                )
              else
                a.setPool(
                  PlanQuota.PoolIdText(in.pool.get.trim),
                  PlanQuota.PoolSet(in.hold.map(PlanQuota.PoolSet.Hold(_)), in.floors.map(PlanQuota.PoolSet.Floors(_)))
                ).left
                  .map(Api.Error(_))
                  .map(AgentsMarkdown.pool)
            case (Some(r), None) if in.fallthrough.isDefined =>
              a.setFallthrough(
                AgentRole.Wire(r),
                PlanQuota.FallthroughSet(PlanQuota.FallthroughSet.Fallthrough(in.fallthrough.get))
              ).left
                .map(Api.Error(_))
                .map(AgentsMarkdown.quota)
            case (Some(r), None) if in.reset =>
              a.resetChain(AgentRole.Wire(r)).left.map(Api.Error(_)).map(AgentsMarkdown.roleChain)
            case (Some(r), None) =>
              a.setChain(
                AgentRole.Wire(r),
                ModelChains.ChainSet(in.models.map(ModelChains.ChainSet.Models(_)))
              ).left
                .map(Api.Error(_))
                .map(AgentsMarkdown.roleChain)
            case (None, Some(t)) if in.reset =>
              a.removeAllowance(ModelChains.AllowanceSet.Target(t))
                .left
                .map(Api.Error(_))
                .map(x =>
                  if x.removed.value then s"The saved allowance of $t is removed." else s"No saved allowance for $t."
                )
            case (None, Some(t)) =>
              in.limits.map(l => ModelChains.Limit.parse(ModelChains.Limit.Spec(l))).partitionMap(identity) match
                case (Nil, ls) =>
                  a.setAllowance(
                    ModelChains.AllowanceSet(
                      ModelChains.AllowanceSet.Target(t),
                      ls.map(_.spec),
                      ModelChains.Allowance.ReservePct(in.reservePct)
                    )
                  ).left
                    .map(Api.Error(_))
                    .map(AgentsMarkdown.allowance)
                case (first :: more, _) => Left(Api.Error(Problems.of(first, more*)))
            case _ =>
              Left(
                Api.Error(
                  Problem.Invalid.Inconsistent
                    .here(
                      NonEmptyVector.of(Problem.Field("role"), Problem.Field("target")),
                      Problem.Invalid.Exclusion.OneOf
                    )
                )
              )
          ToolResult.text(out.fold(McpOut.refusal, identity))
        }

    val manager =
      tool("manager")
        .description(
          "The server's persistent process managers (docs/research-process-managers.md): each drives work items to " +
            "an end — the `review` manager runs `review-article` with fix on one article after another until no MAJOR " +
            "is left, and stops an article with a task when more runs would not help (omission churn, no progress, " +
            "a finding reopened or escalated, too many runs; an article with no coverage filter gets one inferred first); " +
            "`enrich` enriches sources (what an ingest feeds); `seed:<goal>` proposes a research area's articles (a person " +
            "approves the plan) and compiles them; `watch:<goal>` runs a research area's acquisition sweep and currency check " +
            "on its cron (weekly by default; ingesting legal open-access finds only when a person opted it in). `action`: " +
            "`list` (every manager), `show` (one: items in flight, hints, events), `create` (`kind` review|enrich|seed|watch, " +
            "`goal` for seed and watch, `cron` optional; created paused), `pause` (no new " +
            "runs; `runs: true` pauses its runs too), `resume`, `enqueue` (`kind` review|extend|enrich|seed and `target`: " +
            "an article, a citekey/article/goal to enrich, a goal to seed — routed to the right manager, put first), " +
            "`hold` (`target` until `until` a date or `untilSource` a DOI or " +
            "citekey exists), `hint` (`text`: a note its runs' fixer and skeptic read; `item` scopes it to one " +
            "target, `once` to the next run; `effect` = a structured action you PROPOSE — hold/prioritise/skip targets " +
            "or `config` settings — recorded as a hint-effect approval and applied only when a person approves it; " +
            "`suggest: true` asks the server's model to propose one), `autopilot` (no `on`: the status — per research goal " +
            "its stage, managers, sources, articles, open approvals, last activity and pace, and the watchers' ingest " +
            "backlog; `on: true|false` turns the autopilot on — every goal gets its seeding manager and watcher and moves " +
            "from planned to reviewed by itself — or off), `drain` (nothing new starts while the runs in flight finish — the " +
            "autopilot's status says DRAINED once none is queued or running, safe for a deploy; `on: false` lifts it), " +
            "`mode` (the two as one choice, as the web UI shows it: `mode: working` — on, nothing held back; `finish` — " +
            "finish current work, then pause (the drain); `pause` — pause now (the autopilot off)), " +
            "`repair-acquire` (the acquisition items a watcher's sweep created " +
            "for legal open-access works move into the watchers' ingest backlog, to be ingested within the daily caps; a " +
            "person's items are never touched; `dryRun: true` lists what it would do), `focus` (the autopilot's focus: at most " +
            "max-active-goals goals are active for model work, chosen finish-first — articles to review, then to compile, then " +
            "enough sources, depth-first within a tree, then a person's priority, then the oldest; the others' model-using " +
            "items are held, ingestion is not. No `focus`: the active goals with why and what each waits for, the queue, the " +
            "goals that cannot use a slot; `focus: pin|unpin` with `goal`, `focus: priority` with `goal` and `value`, " +
            "`focus: max` with `value` (0 = no cap; no value: back to the config)). Change only what the user asked for."
        )
        .input[ManagerInput]
        .handle { in =>
          val m = a.managing
          val id = ManagerLight.Id(in.id.map(_.trim).filter(_.nonEmpty).getOrElse(Managers.ReviewId))
          def target = in.target
            .map(_.trim)
            .filter(_.nonEmpty)
            .toRight(Api.Error(Problem.Invalid.Missing(Problem.Field("target"), Vector(Problem.Field(in.action)))))
          val out: Either[Api.Error, String] = in.action.trim.toLowerCase match
            case "list"  => Right(ManagersMarkdown.list(m.list))
            case "show"  => m.get(id).map(ManagersMarkdown.detail)
            case "pause" =>
              m.action(id, ManagersControllers.Action("pause"), Some(Managers.ManagerInfo.PauseRuns(in.runs)))
                .map(ManagersMarkdown.line)
            case "resume" => m.action(id, ManagersControllers.Action("resume"), None).map(ManagersMarkdown.line)
            case "create" =>
              m.create(
                Managers.ManagerCreate(
                  Managers.ManagerCreate.Kind(in.kind.getOrElse("")),
                  in.goal.map(Goal.Slug(_)),
                  cron = in.cron.map(Managers.ManagerCreate.Cron(_))
                )
              ).map(ManagersMarkdown.line)
            case "enqueue" =>
              target
                .flatMap(t =>
                  if in.id.exists(_.trim.nonEmpty) then
                    m.addItem(
                      id,
                      Managers.EnqueueRequest(
                        Managers.EnqueueRequest.Kind(in.kind.getOrElse("")),
                        Managers.EnqueueRequest.Target(t),
                        in.priority.map(Managers.EnqueueRequest.Priority(_))
                      )
                    ).map(i => Managers.EnqueueResult(Managers.ManagerInfo.Id(id.value), List(i)))
                  else
                    m.enqueue(
                      Managers.EnqueueRequest(
                        Managers.EnqueueRequest.Kind(in.kind.getOrElse("review")),
                        Managers.EnqueueRequest.Target(t),
                        in.priority.map(Managers.EnqueueRequest.Priority(_))
                      )
                    )
                )
                .map(ManagersMarkdown.enqueued)
            case "hold" =>
              target
                .flatMap(t =>
                  m.patchItem(
                    id,
                    Managers.ManagerItem.Ref(t),
                    Managers.ItemPatch(
                      Managers.ItemPatch.Action("hold"),
                      until = in.until.map(Managers.ItemPatch.Until(_)),
                      untilSource = in.untilSource.map(Managers.ItemPatch.UntilSource(_)),
                      reason = in.text.map(text => Managers.Rationale(s"$text"))
                    )
                  )
                )
                .map(ManagersMarkdown.item)
            case "hint" =>
              in.text
                .map(_.trim)
                .filter(_.nonEmpty)
                .toRight(Api.Error(Problem.Invalid.Missing(Problem.Field("text"), Vector(Problem.Field("hint")))))
                .flatMap(t =>
                  m.addHint(
                    id,
                    Managers.HintAdd(
                      Managers.HintAdd.Text(t),
                      in.item.map(Managers.HintAdd.Item(_)),
                      Some(Managers.HintAdd.Once(in.once)),
                      in.effect,
                      Some(Managers.HintAdd.Author("mcp")),
                      propose = Some(Managers.HintAdd.Propose(in.effect.isDefined)),
                      suggest = Some(Managers.HintAdd.Suggest(in.suggest))
                    )
                  )
                )
                .map(ManagersMarkdown.hint)
            case "autopilot" =>
              in.on match
                case None    => Right(autopilotPage(m.autopilot))
                case Some(b) =>
                  m.setAutopilot(
                    Autopilot.AutopilotSet(Autopilot.AutopilotSet.On(b), Some(Autopilot.AutopilotSet.By("mcp")))
                  ).left
                    .map(Api.Error(_))
                    .map(autopilotPage)
            case "repair-acquire" =>
              repairAcquire(RepairAcquire(in.dryRun))
            case "drain" =>
              m.drain(
                Autopilot.DrainSet(Autopilot.DrainSet.On(in.on.getOrElse(true)), Some(Autopilot.DrainSet.By("mcp")))
              ).left
                .map(Api.Error(_))
                .map(autopilotPage)
            case "mode" =>
              in.mode
                .map(_.trim)
                .filter(_.nonEmpty)
                .toRight(
                  Api.Error(
                    Problem.Invalid.Unknown(
                      Problem.TypeName.of(AgentMode.ModeSet.Mode),
                      Problem.Raw(""),
                      AgentMode.values.toVector.map(v => Problem.Raw.of(v.wire)),
                      Vector(Problem.Field("mode"))
                    )
                  )
                )
                .flatMap(md =>
                  m.setMode(AgentMode.ModeSet(AgentMode.ModeSet.Mode(md), Some(AgentMode.ModeSet.By("mcp"))))
                )
                .map(autopilotPage)
            case "focus" =>
              in.focus.map(_.trim.toLowerCase).filter(v => v.nonEmpty && v != "list") match
                case None    => Right(AutopilotMarkdown.focus(m.focus))
                case Some(v) =>
                  m.setFocus(
                    Autopilot.FocusSet(
                      Autopilot.FocusSet.Action(v),
                      in.goal.map(Goal.Slug(_)),
                      in.value.map(Autopilot.FocusSet.Value(_)),
                      Some(Autopilot.FocusSet.By("mcp"))
                    )
                  ).map(AutopilotMarkdown.focus)
            case other =>
              Left(
                Api.Error(
                  Problem.Invalid.Unknown.here(
                    Problem.TypeName.of(Agents.Action),
                    Problem.Raw(other),
                    Vector(
                      "list",
                      "show",
                      "create",
                      "pause",
                      "resume",
                      "enqueue",
                      "hold",
                      "hint",
                      "autopilot",
                      "mode",
                      "drain",
                      "repair-acquire",
                      "focus"
                    ).map(Problem.Raw(_))
                  )
                )
              )
          ToolResult.text(out.fold(McpOut.refusal, identity))
        }

    val scheduler =
      tool("scheduler")
        .description(
          "The server's central agent scheduler: ONE decision per managers' tick admits the managers' pending work across " +
            "every job type (goal plans, article proposals, candidate sweeps and their ranking, currency checks, enrichment, " +
            "compiles, extensions, coverage filters, reviews) within the AI allowance — the runs at once, each plan pool's " +
            "calls in flight, the budgets and daily caps; a run waiting for a person's approval holds no place. Priorities: " +
            "the goal's (the focus's pins and priorities), the job type's, plus aging; an item that waited max-wait-hours goes " +
            "next, every job type gets a minimum share, goals still gathering sources keep a place. `action`: `status` " +
            "(default: the strategy, resources, the last pass's starts and the queue with why each item waits and what it " +
            "waits behind; `limit` = waiting items shown), `queue` (only the waiting items, in order), `why` (`item`: one " +
            "manager item's id or the target of an open item — why it is where it is), `strategy` (`strategy`: " +
            "type-balanced | goal-first | default — switches it live; change it only when the user asked), `stalls` (the " +
            "unfinished goals with no progress in `days` days, each with the reason it stands still and the command that " +
            "unblocks it)."
        )
        .input[SchedulerInput]
        .handle { in =>
          val m = a.managing
          val out: Either[Api.Error, String] = in.action.trim.toLowerCase match
            case "status" | "" =>
              Right(AgentSchedulerMarkdown.status(m.scheduler(in.limit.flatMap(PageSize.option(_)))))
            case "queue" =>
              val st = m.scheduler(in.limit.orElse(Some(200)).flatMap(PageSize.option(_)))
              Right(if st.waiting.isEmpty then "_Nothing waits._"
              else
                (s"# Waiting (${st.waitingTotal}, strategy ${st.strategy})" :: "" :: st.waiting
                  .map(AgentSchedulerMarkdown.waiting)).mkString("\n"))
            case "why" =>
              in.item
                .map(_.trim)
                .filter(_.nonEmpty)
                .toRight(Api.Error(Problem.Invalid.Missing(Problem.Field("item"), Vector(Problem.Field("why")))))
                .flatMap(i => m.schedulerWhy(Managers.ManagerItem.Ref(i)))
                .map(AgentSchedulerMarkdown.why)
            case "strategy" =>
              in.strategy
                .map(_.trim)
                .filter(_.nonEmpty)
                .toRight(
                  Api.Error(Problem.Invalid.Missing(Problem.Field("strategy"), Vector(Problem.Field("strategy"))))
                )
                .flatMap(n =>
                  m.setScheduler(
                    AgentScheduler.SchedulerSet(
                      AgentScheduler.SchedulerSet.Strategy(n),
                      Some(AgentScheduler.SchedulerSet.By("mcp"))
                    )
                  )
                )
                .map(AgentSchedulerMarkdown.status)
            case "stalls" => Right(StallsMarkdown.report(m.stalls(in.days.map(AgentAutopilotConfig.StallDays(_)))))
            case other    =>
              Left(
                Api.Error(
                  Problem.Invalid.Unknown.here(
                    Problem.TypeName.of(Agents.Action),
                    Problem.Raw(other),
                    Vector("status", "queue", "why", "strategy", "stalls").map(Problem.Raw(_))
                  )
                )
              )
          ToolResult.text(out.fold(McpOut.refusal, identity))
        }

    List(start, status, approvals, decide, links, schedule, models, setModels, manager, scheduler)

  /** `/mcp/agent/<agent>` — one MCP server per agent definition, holding ONLY that definition's read-only tools
    * ([[Toolbox]]: `get_source`, `passages`, `search`, `facets`, `neighbors`, `query` on the read-only role,
    * `get_article`, `followups` without apply). A CLI step reaches it with its run token ([[RunTokenGate]] routes the
    * bare `/mcp/agent` to the token's agent and refuses any other agent's path), so `tools/list` and `tools/call` hold
    * the role's tools whatever the runtime does. A definition that does not load gets no endpoint (and its steps fail
    * with why).
    */
  def agentEndpoints(a: AgentsControllers) =
    a.definitions.agentNames.flatMap { n =>
      a.definitions.agent(n) match
        case Right(d) =>
          List(
            McpServer(tools = d.tools.flatMap(a.toolbox.tool).map(mcpTool(_)))
              .endpoint(List("mcp", "agent", d.name.value))
          )
        case Left(m) =>
          scribe.warn(s"agent MCP: no endpoint for $n: ${m.sentence}")
          Nil
    }

  private def mcpTool[T](t: sttp.ai.core.agent.AgentTool[sttp.shared.Identity, T]) =
    ChimpJson.rawTool(tool(t.name).description(t.description), Toolbox.inputSchema(t)) { json =>
      SttpAiJson
        .arguments(t, json)
        .fold(
          e => ToolResult.error(s"bad arguments for ${t.name}: $e"),
          in => ToolResult.text(t.execute(in))
        )
    }

  final case class ModelsInput(doctor: Boolean = false, offline: Boolean = false, quota: Boolean = false)
      derives JsonCodec
  object ModelsInput:
    given Schema[ModelsInput] = KindlingsSchema
      .derived[ModelsInput]
      .schema
      .modify(_.doctor)(
        _.description("Check every provider instead (keys, model listing, prices, the claude CLI) — nothing is spent")
      )
      .modify(_.offline)(_.description("With doctor: skip the providers' model listings (no network)"))
      .modify(_.quota)(_.description("Show the subscription pools instead: windows, floors, resets, reading age, hold"))

  final case class SetModelsInput(
      role: Option[String] = None,
      models: List[String] = Nil,
      target: Option[String] = None,
      limits: List[String] = Nil,
      reservePct: Int = 0,
      reset: Boolean = false,
      pool: Option[String] = None,
      hold: Option[Boolean] = None,
      floors: Option[String] = None,
      fallthrough: Option[Boolean] = None,
      refresh: Boolean = false
  ) derives JsonCodec
  object SetModelsInput:

    given Schema[SetModelsInput] = KindlingsSchema
      .derived[SetModelsInput]
      .schema
      .modify(_.role)(_.description(s"A step role: ${Agents.Role.values.map(_.wire).mkString(", ")}"))
      .modify(_.models)(
        _.description(
          "The role's chain, first choice first: provider:model (anthropic, openai, local, claude-code, codex, opencode)"
        )
      )
      .modify(_.target)(_.description("An allowance's target: provider:model, or provider:* (e.g. claude-code:*)"))
      .modify(_.limits)(
        _.description(
          "Limits per rolling window: \"<amount> tokens/<window>\" or \"<amount> usd/<window>\"; window 5h, 7d, day, week, month"
        )
      )
      .modify(_.reservePct)(_.description("Stop using it once less than this % of any limit is left (0-99)"))
      .modify(_.reset)(_.description("Drop the saved chain or allowance; the server's config applies again"))
      .modify(_.pool)(_.description("A subscription pool: claude-plan or chatgpt-plan"))
      .modify(_.hold)(_.description("With pool: true pauses all agent use of it, false releases it"))
      .modify(_.floors)(
        _.description(
          "With pool: the % of each window that must stay left, e.g. \"five_hour=30, seven_day=25\"; \"\" = config's"
        )
      )
      .modify(_.fallthrough)(_.description("With role: may its chain fall from a plan below its floor to a paid key"))
      .modify(_.refresh)(
        _.description(
          "Take a fresh reading of `pool` (or every pool) now: ChatGPT through codex app-server (free), Claude through one minimal claude -p turn"
        )
      )

  final case class RunInput(
      pipeline: String,
      target: String,
      model: Option[String] = None,
      budgetUsd: Option[Double] = None,
      fix: Boolean = false,
      rounds: Option[Int] = None,
      acquireItem: Option[Int] = None,
      title: Option[String] = None,
      kind: Option[String] = None,
      query: Option[String] = None,
      topics: List[String] = Nil,
      tags: List[String] = Nil,
      goal: Option[String] = None,
      dryRun: Boolean = false,
      modelFor: List[String] = Nil
  ) derives JsonCodec
  object RunInput:

    given Schema[RunInput] = KindlingsSchema
      .derived[RunInput]
      .schema
      .modify(_.pipeline)(_.description(s"The pipeline: ${Agents.Pipeline.values.map(_.wire).mkString(", ")}"))
      .modify(_.target)(
        _.description(
          "What it works on: a citekey (enrich-source), an article slug (review-article; " +
            "compile-article — a new slug creates the article, an existing one is extended), a goal slug (plan-goal, " +
            "currency-check, acquisition-sweep)"
        )
      )
      .modify(_.title)(_.description("compile-article: the title of a NEW article"))
      .modify(_.kind)(
        _.description(
          "compile-article: the kind of a new article (concept, myth, culture, region, timeline, report, index)"
        )
      )
      .modify(_.query)(_.description("compile-article: free-text filter for the sources to compile from"))
      .modify(_.topics)(_.description("compile-article: topics the sources must all have"))
      .modify(_.tags)(_.description("compile-article: tags the sources must all have"))
      .modify(_.goal)(_.description("The goal the run works for (a compiled article is linked to it)"))
      .modify(_.model)(_.description("provider:model for every step of this run (default: the configured roles)"))
      .modify(_.modelFor)(
        _.description(
          s"role=provider:model: one role's model for this run only, over its chain and over `model` " +
            s"(roles: ${Agents.Role.values.map(_.wire).mkString(", ")}); one entry per role"
        )
      )
      .modify(_.dryRun)(
        _.description(
          "Do every read and every model step but write nothing to the corpus: each write is recorded on its step (for comparing models)"
        )
      )
      .modify(_.budgetUsd)(_.description("This run's cost cap in USD (default: the server's agents.budgets.run-usd)"))
      .modify(_.fix)(
        _.description(
          "review-article: let the fixer revise the article between passes, each fix verified by the reviewers"
        )
      )
      .modify(_.rounds)(
        _.description(
          "review-article: at most this many review passes: a full panel, then a verification pass after each fix (default: agents.review.max-rounds)"
        )
      )
      .modify(_.acquireItem)(
        _.description("enrich-source: the acquisition item this run fulfils (ticked off at the end)")
      )

  final case class RunsInput(
      id: Option[String] = None,
      status: Option[String] = None,
      limit: Option[Int] = None,
      messages: Boolean = false,
      goal: Option[String] = None,
      compare: List[String] = Nil,
      before: Option[String] = None,
      manager: Option[String] = None,
      pipeline: List[String] = Nil,
      origin: List[String] = Nil,
      model: List[String] = Nil,
      provider: List[String] = Nil,
      since: Option[String] = None,
      until: Option[String] = None,
      subject: Option[String] = None
  ) derives JsonCodec:
    /** The listing's filter ([[ListFilters.RunFilter]]): `status` and `manager` may be comma-separated. */
    def filter: ListFilters.RunFilter =
      ListFilters.RunFilter(
        status.toList.map(ListFilters.RunFilter.Status(_)),
        pipeline.map(ListFilters.RunFilter.Pipeline(_)),
        manager.toList.map(ListFilters.RunFilter.Manager(_)),
        goal.map(Goal.Slug(_)),
        model.map(ListFilters.RunFilter.Model(_)),
        provider.map(ListFilters.RunFilter.Provider(_)),
        origin.map(ListFilters.RunFilter.Origin(_)),
        since.map(ListFilters.RunFilter.Since(_)),
        until.map(ListFilters.RunFilter.Until(_)),
        subject.map(ListFilters.RunFilter.Subject(_))
      )
  object RunsInput:

    given Schema[RunsInput] = KindlingsSchema
      .derived[RunsInput]
      .schema
      .modify(_.manager)(
        _.description(
          "List only the runs these process managers dispatched (e.g. review, seed:<goal>; comma-separated)"
        )
      )
      .modify(_.id)(_.description("One run's id; omit to list runs"))
      .modify(_.status)(_.description("List only runs in these states (comma-separated)"))
      .modify(_.pipeline)(
        _.description(s"List only runs of these pipelines (${Agents.Pipeline.values.map(_.wire).mkString(", ")})")
      )
      .modify(_.origin)(
        _.description(s"List only runs started by these (${Agents.Origin.values.map(_.wire).mkString(", ")})")
      )
      .modify(_.model)(_.description("List only runs a step of which used one of these provider:model"))
      .modify(_.provider)(
        _.description("List only runs a step of which used one of these providers (codex, claude-code, …)")
      )
      .modify(_.since)(_.description("Started at or after: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago"))
      .modify(_.until)(_.description("Started before the end of: 2026-10-01, or an ISO instant"))
      .modify(_.subject)(_.description("Text in the run's target (a citekey, an article or goal slug)"))
      .modify(_.messages)(_.description("With id: include the traced messages and tool calls"))
      .modify(_.goal)(_.description("List only the runs for this goal"))
      .modify(_.compare)(_.description("Two or more run ids to compare side by side (one pipeline, one target)"))
      .modify(_.before)(_.description("Listing: the next older page — the id of the last run shown"))

  final case class GoalLinkInput(
      goal: String,
      kind: String,
      ref: String,
      note: Option[String] = None,
      remove: Boolean = false
  ) derives JsonCodec
  object GoalLinkInput:

    given Schema[GoalLinkInput] = KindlingsSchema
      .derived[GoalLinkInput]
      .schema
      .modify(_.goal)(_.description("The goal's slug"))
      .modify(_.kind)(_.description("source | article | query | queue-item"))
      .modify(_.ref)(_.description("A citekey, an article slug, the query text, or a queue item id"))
      .modify(_.note)(_.description("Why it is linked"))
      .modify(_.remove)(_.description("Unlink instead of link"))

  final case class ScheduleInput(
      goal: Option[String] = None,
      kind: Option[String] = None,
      cron: Option[String] = None,
      enabled: Boolean = true,
      budgetUsd: Option[Double] = None,
      remove: Boolean = false
  ) derives JsonCodec
  object ScheduleInput:

    given Schema[ScheduleInput] = KindlingsSchema
      .derived[ScheduleInput]
      .schema
      .modify(_.goal)(_.description("The goal's slug (omit with kind to list every schedule)"))
      .modify(_.kind)(_.description("currency | acquisition"))
      .modify(_.cron)(
        _.description(
          "Five-field cron in UTC (minute hour day-of-month month day-of-week), or @daily, @weekly, @monthly"
        )
      )
      .modify(_.enabled)(_.description("false takes the sweep out of the watcher (its cron stays)"))
      .modify(_.budgetUsd)(_.description("The cost cap of each run it starts (default: the goal's, then the server's)"))
      .modify(_.remove)(_.description("Delete the schedule"))

  final case class ApprovalsInput(
      state: Option[String] = None,
      limit: Option[Int] = None,
      before: Option[String] = None,
      kind: List[String] = Nil,
      subject: Option[String] = None,
      goal: Option[String] = None,
      decidedBy: List[String] = Nil,
      digest: Boolean = false
  ) derives JsonCodec:
    def filter: ListFilters.ApprovalFilter = ListFilters.ApprovalFilter(
      state.map(ListFilters.ApprovalFilter.State(_)),
      kind.map(ListFilters.ApprovalFilter.Kind(_)),
      subject.map(ListFilters.ApprovalFilter.Subject(_)),
      goal.map(Goal.Slug(_)),
      decidedBy.map(ListFilters.ApprovalFilter.DecidedBy(_))
    )
  object ApprovalsInput:

    given Schema[ApprovalsInput] = KindlingsSchema
      .derived[ApprovalsInput]
      .schema
      .modify(_.state)(_.description("open (default), approved, rejected, failed, or all"))
      .modify(_.kind)(_.description(s"Only these kinds: ${Agents.ApprovalKind.values.map(_.wire).mkString(", ")}"))
      .modify(_.subject)(
        _.description(
          "Text in what it is about: a goal or article slug, a manager, a citekey, the proposing run's goal"
        )
      )
      .modify(_.goal)(_.description("Only one goal's: its plan, or proposed by a run for it"))
      .modify(_.decidedBy)(
        _.description("Only those decided by: person, autopilot (its checks) or auto (applied when proposed)")
      )
      .modify(_.before)(_.description("The next older page — the id of the last approval shown"))
      .modify(_.digest)(
        _.description("true: the digest of everything waiting for a person, by kind, instead of the listing")
      )

  final case class DecideInput(
      id: Option[Long] = None,
      decision: String,
      note: Option[String] = None,
      kind: Option[String] = None,
      subject: Option[String] = None,
      ids: List[Long] = Nil,
      dryRun: Boolean = false,
      limit: Option[Int] = None
  ) derives JsonCodec
  object DecideInput:

    given Schema[DecideInput] = KindlingsSchema
      .derived[DecideInput]
      .schema
      .modify(_.id)(
        _.description("The approval's id (from list_approvals); without it, a group at once (kind, subject, ids)")
      )
      .modify(_.decision)(_.description("approve or reject"))
      .modify(_.note)(_.description("Why, kept with the decision"))
      .modify(_.kind)(_.description("A group: every open approval of this kind"))
      .modify(_.subject)(_.description("A group: every open approval about this goal or article"))
      .modify(_.ids)(_.description("A group: these approvals"))
      .modify(_.dryRun)(_.description("A group: list what it would decide, decide nothing"))
      .modify(_.limit)(_.description("A group: at most this many, the oldest first"))

  final case class SchedulerInput(
      action: String = "status",
      item: Option[String] = None,
      strategy: Option[String] = None,
      limit: Option[Int] = None,
      days: Option[Int] = None
  ) derives JsonCodec
  object SchedulerInput:

    given Schema[SchedulerInput] = KindlingsSchema
      .derived[SchedulerInput]
      .schema
      .modify(_.action)(_.description("status (default) | queue | why | strategy | stalls"))
      .modify(_.days)(_.description("stalls: no progress in this many days (default agents.autopilot.stall-days)"))
      .modify(_.item)(_.description("why: a manager item's id, or the target (slug, citekey, goal) of an open item"))
      .modify(_.strategy)(
        _.description("strategy: type-balanced | goal-first | default (back to agents.scheduler.strategy)")
      )
      .modify(_.limit)(_.description("status, queue: how many waiting items to show (default 50; queue 200)"))

  final case class ManagerInput(
      action: String = "list",
      id: Option[String] = None,
      target: Option[String] = None,
      text: Option[String] = None,
      item: Option[String] = None,
      once: Boolean = false,
      runs: Boolean = false,
      priority: Option[Int] = None,
      until: Option[String] = None,
      untilSource: Option[String] = None,
      kind: Option[String] = None,
      goal: Option[String] = None,
      cron: Option[String] = None,
      effect: Option[Managers.HintEffect] = None,
      suggest: Boolean = false,
      on: Option[Boolean] = None,
      dryRun: Boolean = false,
      focus: Option[String] = None,
      value: Option[Int] = None,
      mode: Option[String] = None
  ) derives JsonCodec
  object ManagerInput:

    given Schema[ManagerInput] = KindlingsSchema
      .derived[ManagerInput]
      .schema
      .modify(_.action)(
        _.description(
          "list | show | create | pause | resume | enqueue | hold | hint | autopilot | drain | repair-acquire | focus"
        )
      )
      .modify(_.focus)(_.description("focus: list (default) | pin | unpin | priority | max"))
      .modify(_.value)(
        _.description(
          "focus priority: the goal's priority (higher first); focus max: the cap (0 = none; absent: the config's)"
        )
      )
      .modify(_.on)(
        _.description("autopilot: true turns it on, false off; absent: its status. drain: false lifts the drain")
      )
      .modify(_.dryRun)(_.description("repair-acquire: list what it would do, change nothing"))
      .modify(_.id)(_.description("The manager (default: review; enqueue without it routes by kind)"))
      .modify(_.target)(_.description("enqueue, hold: an article slug, a citekey or a goal (by kind)"))
      .modify(_.kind)(
        _.description(
          "create: review | enrich | seed | watch; enqueue: review | extend | enrich | seed (with id: the item kind)"
        )
      )
      .modify(_.goal)(_.description("create seed, watch: the research area; focus pin, unpin, priority: the goal"))
      .modify(_.cron)(
        _.description("create: when it fires, five fields in UTC or @daily/@weekly/@monthly (watch: default @weekly)")
      )
      .modify(_.text)(_.description("hint: the note its runs read; hold: why"))
      .modify(_.item)(_.description("hint: scope it to this target (or item id)"))
      .modify(_.once)(_.description("hint: for the next run only"))
      .modify(_.effect)(
        _.description(
          "hint: a structured action you propose (action prioritise|hold|skip with targets/tag/until/" +
            "untilSource/priority, or config with config settings); a person approves it before it is applied"
        )
      )
      .modify(_.suggest)(
        _.description("hint: ask the server's model to propose the action the text asks for (a person approves it)")
      )
      .modify(_.runs)(_.description("pause: pause its runs in flight too"))
      .modify(_.priority)(_.description("enqueue: the priority (default 10, first)"))
      .modify(_.until)(_.description("hold: until this date (2026-11-01)"))
      .modify(_.untilSource)(_.description("hold: until a source with this DOI or citekey is in the corpus"))
