package com.histmersion.domain.automation.approvals

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.agents.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.AutopilotPolicy.{DraftFacts, GoalPlanFacts, PlannedFacts, Verdict}
import com.histmersion.api.automation.Goal.Plans.DraftMode
import com.histmersion.api.automation.Managers.Feed
import com.histmersion.api.automation.Queue.QueueAdd

import java.time.Instant
import javax.sql.DataSource
import com.histmersion.api.automation.Managers
import com.histmersion.api.automation.Goal

/** The checked auto approvals (docs/research-process-managers.md "As built: the autopilot"): on every managers' tick,
  * each OPEN approval of the kinds a person used to decide by hand — an `article-plan` (propose-articles), a
  * `goal-plan` (plan-goal), an `article-publish` (compile-article) — is decided by code when its mode says so
  * (`agents.approvals.<kind>`, or the `autoApprove` setting of the manager whose run opened it): [[AutopilotPolicy]]
  * checks it against the corpus as it is NOW and against the day's caps. Approved or rejected, it goes through the very
  * decision a person's click takes ([[AgentsHandlers.decide]]: a blocking run is woken, an approved plan becomes
  * compile items), with a note that starts `autopilot:` and says why, an `approval` event on the manager, and a worklog
  * line — so a person can audit (and undo) every one. Left (a cap reached, a plan too large for the cap), it waits for
  * a person or the next day; the manager says so once.
  */
final class AutoApprovals(
    ds: DataSource,
    cfg: => AgentsConfig,
    agents: AgentsHandlers
) extends ApprovalChecks:

  /** The kinds decided here. */
  val Kinds: List[ApprovalKind] = List(ApprovalKind.ArticlePlan, ApprovalKind.GoalPlan, ApprovalKind.ArticlePublish)

  /** At most this many approvals decided by code looked at in one tick (the rest the next). */
  private val PerTick = 50

  /** The run that opened an approval, and the manager that dispatched that run. */
  private def runOf(a: ApprovalInfo): Option[RunInfo] = a.runId.flatMap(agents.runs.get(_))

  /** Does the supervisor decide `a`? The manager's `autoApprove` wins; else `agents.approvals.<kind>`. */
  def checked(a: ApprovalInfo, manager: Option[Managers.ManagerInfo]): Boolean =
    a.approvalKind.exists(k =>
      Kinds.contains(k) &&
        manager
          .flatMap(_.config.autoApprove)
          .getOrElse(cfg.approvals.checked(k.transformInto[com.histmersion.domain.automation.Approval.Kind]))
    )

  /** Decide what can be decided now; one line per decision (for the tick's log). */
  def sweep(now: Instant): List[String] =
    // the goals the scheduler puts first, first: a daily cap is spent on the highest-priority goals (a pin, a
    // person's priority), then the oldest goals; approvals about no known goal last, oldest first
    // the window is taken among the approvals code decides: those a person decides never crowd it out
    // while goal plans wait (too many unfinished goals), an open one is left alone: not decided, no event
    val kinds = if agents.focus.goalPlansWait().isDefined then Kinds.filterNot(_ == ApprovalKind.GoalPlan) else Kinds
    val open = agents.approvalStore.openOfKinds(kinds, Int.MaxValue).flatMap { a =>
      val run = runOf(a)
      val mgr = run.flatMap(_.managerId).flatMap(agents.managerStore.get(_))
      Option.when(checked(a, mgr))(Candidate(a, run, mgr))
    }
    val goals = agents.focusStore.goals().map(g => g.slug -> g).toMap
    val linked = agents.focusStore
      .goalsOf(Goal.Plans.LinkKind.Article.wire, open.flatMap(_.approval.subject).distinct)
    def goalOf(a: ApprovalInfo) =
      a.subject.flatMap(s => goals.get(s).orElse(linked.getOrElse(s, Nil).flatMap(goals.get).headOption))
    val ordered = open.sortBy { c =>
      val g = goalOf(c.approval)
      (
        g.fold(0)(x => -(x.priority + (if x.pinned then SchedulerPolicy.PinBoost else 0))),
        g.fold("\uffff")(_.createdAt),
        c.approval.id
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
    AutopilotPolicy.note(verdict) match
      case None =>
        val why = verdict match
          case Verdict.Leave(w) => w
          case _                => ""
        mgr.foreach { m =>
          val text = s"approval #${a.id} (${a.kind}) left for a person: $why"
          if !agents.managerStore.lastEventOf(m.id, Feed.Approval).exists(_.text == text) then
            agents.managerStore.event(m.id, run.flatMap(_.managerItem), a.runId, Feed.Approval, text)
        }
        Nil
      case Some(note) =>
        val decision = verdict match
          case Verdict.Approve(_) => Decision.Approve
          case _                  => Decision.Reject
        agents.decide(a.id, decision.wire, DecisionRequest(Some(note))) match
          case Left(e) =>
            scribe.info(s"autopilot: $what not decided: ${e.message}")
            Nil
          case Right(d) =>
            val text =
              s"$what ${if decision == Decision.Approve then "approved" else "rejected"} by the autopilot's checks: " +
                note
                  .stripPrefix(AutopilotPolicy.NotePrefix)
                  .trim + (if d.state == ApprovalState.Failed.wire then
                             s" — applying it failed: ${d.result.getOrElse("")}"
                           else "")
            mgr.foreach(m =>
              agents.managerStore.event(m.id, run.flatMap(_.managerItem), a.runId, Feed.Approval, text.take(2000))
            )
            val _ = agents.workLists.add("log", QueueAdd(s"autopilot: $text".take(1000)))
            scribe.info(s"autopilot: $text")
            List(s"$what: ${decision.wire}")

  /** Today's cap of automatic goal-plan approvals: `goal-plans-per-day`, scaled to the open plans when
    * `goal-plans-backlog-share` is above 0 (at most `goal-plans-ceiling`).
    */
  def goalPlanCap: Int =
    val c = cfg.approvals.auto
    AutopilotPolicy.scaledCap(
      c.goalPlansPerDay,
      agents.approvalStore.openCount(ApprovalKind.GoalPlan),
      c.goalPlansBacklogShare,
      c.goalPlansCeiling
    )

  /** Today's cap of automatic publishes, scaled the same way to the open drafts. */
  def publishCap: Int =
    val c = cfg.approvals.auto
    AutopilotPolicy.scaledCap(
      c.publishPerDay,
      agents.approvalStore.openCount(ApprovalKind.ArticlePublish),
      c.publishBacklogShare,
      c.publishCeiling
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
          case (_, Some(wait)) => Verdict.Leave(wait)
          case (None, _)       => Verdict.Leave("the approval carries no readable plan")
          case (Some(p), _)    =>
            val goal = a.subject.getOrElse(p.goal)
            val depth = AutopilotPolicy.depth(goal, s => agents.goals.get(s).flatMap(_.parentSlug))
            AutopilotPolicy.goalPlan(
              GoalPlanFacts(goal, p.subGoals.size, p.queries.size, p.works.size, depth),
              caps.maxGoalDepth,
              agents.approvalStore.autoApprovedSince(ApprovalKind.GoalPlan, day).size,
              goalPlanCap
            ) match
              // decided by code whatever the focus: only the sub-goals it would open are bounded
              case v @ Verdict.Approve(_) => agents.focus.goalPlanBlock(goal, p.subGoals.size).fold(v)(Verdict.Leave(_))
              case v                      => v
      case _ => Verdict.Leave("not a kind the autopilot decides")

  def checkpoint(run: String, step: String): Option[String] =
    agents.runs
      .steps(run, 100000)
      .filter(s => s.name == step && s.status == StepStatus.Done.wire)
      .lastOption
      .flatMap(_.output)

  def config: AgentsConfig = cfg
  def slugExists(slug: String): Boolean = agents.host.articleExists(slug)
  def autoApprovedSince(kind: com.histmersion.domain.automation.Approval.Kind, since: Instant): List[ApprovalInfo] = agents.approvalStore.autoApprovedSince(io.scalaland.chimney.Transformer.derive[com.histmersion.domain.automation.Approval.Kind, ApprovalKind].transform(kind), since)
