package com.histmersion
package domain.automation.approvals
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.agents.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.AutopilotPolicy.{DraftFacts, GoalPlanFacts, PlannedFacts, Verdict}
import com.histmersion.api.automation.Goal.Plans.DraftMode
import com.histmersion.api.automation.Managers.{Feed, ManagerEvent}
import com.histmersion.api.automation.Queue.{QueueAdd, QueueItem}
import com.histmersion.domain.automation.goals.FocusRepo
import com.histmersion.domain.automation.{Approval as DomainApproval, Run}
import com.histmersion.util.jobs.Job

import java.time.Instant
import javax.sql.DataSource
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.Goal
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.agents.AgentRepo

/** The checked auto approvals (docs/research-process-managers.md "As built: the autopilot"): on every managers' tick,
  * each OPEN approval of the kinds a person used to decide by hand — an `article-plan` (propose-articles), a
  * `goal-plan` (plan-goal), an `article-publish` (compile-article) — is decided by code when its mode says so
  * (`agents.approvals.<kind>`, or the `autoApprove` setting of the manager whose run opened it): [[AutopilotPolicy]]
  * checks it against the corpus as it is NOW and against the day's caps. Approved or rejected, it goes through the very
  * decision a person's click takes ([[AgentsControllers.decide]]: a blocking run is woken, an approved plan becomes
  * compile items), with a note that starts `autopilot:` and says why, an `approval` event on the manager, and a worklog
  * line — so a person can audit (and undo) every one. Left (a cap reached, a plan too large for the cap), it waits for
  * a person or the next day; the manager says so once.
  */
final class AutoApprovals(
    ds: DataSource,
    cfg: => AgentsConfig,
    agents: AgentsControllers
) extends ApprovalChecks:

  /** The kinds decided here. */
  val Kinds: List[ApprovalKind] = List(ApprovalKind.ArticlePlan, ApprovalKind.GoalPlan, ApprovalKind.ArticlePublish)

  /** At most this many approvals decided by code looked at in one tick (the rest the next). */
  private val PerTick = 50

  /** The run that opened an approval, and the manager that dispatched that run. */
  private def runOf(a: ApprovalInfo): Option[RunInfo] =
    a.runId.flatMap(id => Run.Id.option(id.value)).flatMap(agents.runs.get(_))

  /** Does the supervisor decide `a`? The manager's `autoApprove` wins; else `agents.approvals.<kind>`. */
  def checked(a: ApprovalInfo, manager: Option[Managers.ManagerInfo]): Boolean =
    a.approvalKind.exists(k =>
      Kinds.contains(k) &&
        manager
          .flatMap(_.config.autoApprove.map(_.value))
          .getOrElse(cfg.approvals.checked(k.transformInto[Approval.Kind]))
    )

  /** Decide what can be decided now; one line per decision (for the tick's log). */
  def sweep(now: Instant): List[String] =
    // the goals the scheduler puts first, first: a daily cap is spent on the highest-priority goals (a pin, a
    // person's priority), then the oldest goals; approvals about no known goal last, oldest first
    // the window is taken among the approvals code decides: those a person decides never crowd it out
    // while goal plans wait (too many unfinished goals), an open one is left alone: not decided, no event
    val kinds = if agents.focus.goalPlansWait().isDefined then Kinds.filterNot(_ == ApprovalKind.GoalPlan) else Kinds
    val open = agents.approvalStore.openOfKinds(kinds, ApprovalQuery.Limit(Int.MaxValue)).flatMap { a =>
      val run = runOf(a)
      val mgr = run.flatMap(_.managerId).flatMap(agents.managerStore.get(_))
      Option.when(checked(a, mgr))(Candidate(a, run, mgr))
    }
    val goals = agents.focusStore.goals().map(g => g.slug -> g).toMap
    val linked = agents.focusStore
      .goalsOf(
        com.histmersion.domain.automation.Goal.LinkKind.Article.wire,
        open.flatMap(_.approval.subject).distinct.map(s => Goal.Plans.GoalLink.Ref(s.value))
      )
    def goalOf(a: ApprovalInfo) =
      a.subject.flatMap(s =>
        goals
          .get(FocusRepo.GoalRow.Slug(s.value))
          .orElse(
            linked
              .getOrElse(Goal.Plans.GoalLink.Ref(s.value), Nil)
              .flatMap(g => goals.get(FocusRepo.GoalRow.Slug(g.value)))
              .headOption
          )
      )
    val ordered = open.sortBy[(Int, String, Long)] { c =>
      val g = goalOf(c.approval)
      (
        g.fold(0)(x => -(x.priority.value + (if x.pinned.value then SchedulerPolicy.PinBoost else 0))),
        g.fold("\uffff")(_.createdAt.value),
        c.approval.id.value
      )
    }
    ordered.take(PerTick).flatMap { c =>
      try decide(c.approval, c.run, c.manager, now)
      catch
        case e: InterruptedException => throw e
        case e: Exception            =>
          scribe.warn(s"autopilot: approval #${c.approval.id} not checked: ${e.getMessage}", e)
          Nil
    }

  final private case class Candidate(
      approval: ApprovalInfo,
      run: Option[RunInfo],
      manager: Option[Managers.ManagerInfo]
  )

  private def decide(
      a: ApprovalInfo,
      run: Option[RunInfo],
      mgr: Option[Managers.ManagerInfo],
      now: Instant
  ): List[String] =
    val verdict = judge(a, run, mgr, now)
    val what = s"${a.kind} #${a.id}${a.subject.fold("")(s => s" ($s)")}"
    val runId = a.runId.flatMap(id => Run.Id.option(id.value))
    AutopilotPolicy.note(verdict) match
      case None =>
        val why = verdict match
          case Verdict.Leave(w) => s"$w"
          case _                => ""
        mgr.foreach { m =>
          val text = s"approval #${a.id} (${a.kind}) left for a person: $why"
          if !agents.managerStore.lastEventOf(m.id, ManagerEvent.Kind(Feed.Approval)).exists(_.text.value == text) then
            agents.managerStore.event(
              m.id,
              run.flatMap(_.managerItem),
              runId,
              ManagerEvent.Kind(Feed.Approval),
              ManagerEvent.Text(text)
            )
        }
        Nil
      case Some(note) =>
        val decision = verdict match
          case Verdict.Approve(_) => Decision.Approve
          case _                  => Decision.Reject
        agents.decide(
          DomainApproval.Id(a.id.value),
          DomainApproval.Outcome(decision.wire.value),
          DecisionRequest(Some(DecisionRequest.Note(note.value)))
        ) match
          case Left(e) =>
            scribe.info(s"autopilot: $what not decided: ${e.message.sentence}")
            Nil
          case Right(d) =>
            val text =
              s"$what ${if decision == Decision.Approve then "approved" else "rejected"} by the autopilot's checks: " +
                note.withoutAutopilotMark.value + (if d.state == ApprovalState.Failed.wire then
                                                     s" — applying it failed: ${d.result.getOrElse("")}"
                                                   else "")
            mgr.foreach(m =>
              agents.managerStore.event(
                m.id,
                run.flatMap(_.managerItem),
                runId,
                ManagerEvent.Kind(Feed.Approval),
                ManagerEvent.Text(text.take(2000))
              )
            )
            val _ = agents.workLists.add(
              WorkListControllers.ListName("log"),
              QueueAdd(QueueItem.Text(s"autopilot: $text".take(1000)))
            )
            scribe.info(s"autopilot: $text")
            List(s"$what: ${decision.wire}")

  /** Today's cap of automatic goal-plan approvals: `goal-plans-per-day`, scaled to the open plans when
    * `goal-plans-backlog-share` is above 0 (at most `goal-plans-ceiling`).
    */
  def goalPlanCap: AutopilotPolicy.Cap =
    val c = cfg.approvals.auto
    AutopilotPolicy.scaledCap(
      AutopilotPolicy.Base(c.goalPlansPerDay.value),
      AutopilotPolicy.Backlog(agents.approvalStore.openCount(ApprovalKind.GoalPlan).value),
      AutopilotPolicy.Share(c.goalPlansBacklogShare.value),
      AutopilotPolicy.Cap(c.goalPlansCeiling.value)
    )

  /** Today's cap of automatic publishes, scaled the same way to the open drafts. */
  def publishCap: ApprovalChecks.PublishCap =
    val c = cfg.approvals.auto
    ApprovalChecks.PublishCap(
      AutopilotPolicy
        .scaledCap(
          AutopilotPolicy.Base(c.publishPerDay.value),
          AutopilotPolicy.Backlog(agents.approvalStore.openCount(ApprovalKind.ArticlePublish).value),
          AutopilotPolicy.Share(c.publishBacklogShare.value),
          AutopilotPolicy.Cap(c.publishCeiling.value)
        )
        .value
    )

  /** The checks of one approval as the corpus is now. */
  def judge(a: ApprovalInfo, run: Option[RunInfo], mgr: Option[Managers.ManagerInfo], now: Instant): Verdict =
    agents.workflows.managerKinds.view
      .flatMap(_.judge(this, a, run, mgr, now))
      .headOption
      .getOrElse(judgeOwn(a, now))

  /** The approvals the engine's own workflows open (a goal's plan). */
  private def judgeOwn(a: ApprovalInfo, now: Instant): Verdict =
    val day = ManagerPolicy.dayStart(now)
    val caps = cfg.approvals.auto
    a.approvalKind match
      case Some(ApprovalKind.GoalPlan) =>
        (a.plan, agents.focus.goalPlansWait()) match
          case (_, Some(wait)) => Verdict.Leave(AutopilotPolicy.Rationale(wait.value))
          case (None, _)       => Verdict.Leave(AutopilotPolicy.Rationale("the approval carries no readable plan"))
          case (Some(p), _)    =>
            val raw = a.subject.fold(p.goal.value)(_.value)
            (com.histmersion.domain.automation.Goal.Slug.option(raw), Slug.option(raw)) match
              case (Some(goal), Some(slug)) =>
                val depth = AutopilotPolicy.depth(
                  slug,
                  s =>
                    com.histmersion.domain.automation.Goal.Slug
                      .option(s.value)
                      .flatMap(agents.goals.get)
                      .flatMap(_.parentSlug)
                      .flatMap(parent => Slug.option(parent.value))
                )
                AutopilotPolicy.goalPlan(
                  GoalPlanFacts(
                    GoalPlanFacts.Goal(raw),
                    GoalPlanFacts.SubGoals(p.subGoals.size),
                    GoalPlanFacts.Queries(p.queries.size),
                    GoalPlanFacts.Works(p.works.size),
                    depth
                  ),
                  AutopilotPolicy.DepthLimit(caps.maxGoalDepth.value),
                  AutopilotPolicy.ApprovedToday(
                    agents.approvalStore.autoApprovedSince(ApprovalKind.GoalPlan, day).size
                  ),
                  goalPlanCap
                ) match
                  // decided by code whatever the focus: only the sub-goals it would open are bounded
                  case v @ Verdict.Approve(_) =>
                    agents.focus
                      .goalPlanBlock(goal, AutopilotPolicy.Proposed(p.subGoals.size))
                      .fold(v)(d => Verdict.Leave(AutopilotPolicy.Rationale(d.value)))
                  case v => v
              case _ => Verdict.Leave(AutopilotPolicy.Rationale("the approval names no valid goal"))
      case _ => Verdict.Leave(AutopilotPolicy.Rationale("not a kind the autopilot decides"))

  def checkpoint(run: Run.Id, step: AgentRepo.StepRecord.Name): Option[String] =
    agents.runs
      .steps(Job.Id.assume(run.value), AgentRepo.OutputChars(100000))
      .filter(s => s.name.value == step.value && s.status.value == StepStatus.Done.wire.value)
      .lastOption
      .flatMap(_.output.map(_.value))

  def config: AgentsConfig = cfg
  def slugExists(slug: ApprovalChecks.Slug): Boolean = Slug.option(slug.value).exists(agents.host.articleExists)
  def autoApprovedSince(kind: Approval.Kind, since: Instant): List[ApprovalInfo] = agents.approvalStore
    .autoApprovedSince(io.scalaland.chimney.Transformer.derive[Approval.Kind, ApprovalKind].transform(kind), since)
