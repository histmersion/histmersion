package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.AutopilotPolicy.{DraftFacts, GoalPlanFacts, PlannedFacts, Verdict}
import com.histmersion.core.GoalPlans.DraftMode
import com.histmersion.core.Managers.Feed
import com.histmersion.core.Queue.QueueAdd
import com.histmersion.db.{AgentRepo, GoalRepo, ManagerRepo}

import java.time.Instant
import javax.sql.DataSource

/** The checked auto approvals (docs/research-process-managers.md "As built: the autopilot"): on every
  * managers' tick, each OPEN approval of the kinds a person used to decide by hand — an `article-plan`
  * (propose-articles), a `goal-plan` (plan-goal), an `article-publish` (compile-article) — is decided by
  * code when its mode says so (`agents.approvals.<kind>`, or the `autoApprove` setting of the manager
  * whose run opened it): [[AutopilotPolicy]] checks it against the corpus as it is NOW and against the
  * day's caps. Approved or rejected, it goes through the very decision a person's click takes
  * ([[AgentsHandlers.decide]]: a blocking run is woken, an approved plan becomes compile items), with a
  * note that starts `autopilot:` and says why, an `approval` event on the manager, and a worklog line —
  * so a person can audit (and undo) every one. Left (a cap reached, a plan too large for the cap), it
  * waits for a person or the next day; the manager says so once. */
final class AutoApprovals(ds: DataSource, cfg: => AgentsConfig, h: com.histmersion.server.Handlers, agents: AgentsHandlers):

  /** The kinds decided here. */
  val Kinds: List[ApprovalKind] = List(ApprovalKind.ArticlePlan, ApprovalKind.GoalPlan, ApprovalKind.ArticlePublish)

  /** At most this many open approvals looked at in one tick (the rest the next). */
  private val PerTick = 50

  /** The run that opened an approval, and the manager that dispatched that run. */
  private def runOf(a: ApprovalInfo): Option[RunInfo] = a.runId.flatMap(AgentRepo.get(ds, _))

  /** Does the supervisor decide `a`? The manager's `autoApprove` wins; else `agents.approvals.<kind>`. */
  def checked(a: ApprovalInfo, manager: Option[Managers.ManagerInfo]): Boolean =
    a.approvalKind.exists(k => Kinds.contains(k) &&
      manager.flatMap(_.config.autoApprove).getOrElse(cfg.approvals.checked(k)))

  /** Decide what can be decided now; one line per decision (for the tick's log). */
  def sweep(now: Instant): List[String] =
    // the focus's active goals first: a daily cap is spent on the goals being finished
    val open = AgentRepo.openOfKinds(ds, Kinds, PerTick)
    val ordered = agents.focus.activeGoals match
      case None => open
      case Some(active) =>
        val linked = com.histmersion.db.FocusRepo.goalsOf(ds, GoalPlans.LinkKind.Article.wire, open.flatMap(_.subject).distinct)
        def mine(a: ApprovalInfo) = a.subject.exists(s => active(s) || linked.getOrElse(s, Nil).exists(active))
        open.filter(mine) ++ open.filterNot(mine)
    ordered.flatMap { a =>
      val run = runOf(a)
      val mgr = run.flatMap(_.managerId).flatMap(ManagerRepo.get(ds, _))
      if !checked(a, mgr) then Nil
      else
        try decide(a, run, mgr, now)
        catch
          case e: InterruptedException => throw e
          case e: Exception =>
            scribe.warn(s"autopilot: approval #${a.id} not checked: ${e.getMessage}", e)
            Nil
    }

  private def decide(a: ApprovalInfo, run: Option[RunInfo], mgr: Option[Managers.ManagerInfo], now: Instant): List[String] =
    val verdict = judge(a, run, mgr, now)
    val what = s"${a.kind} #${a.id}${a.subject.fold("")(s => s" ($s)")}"
    AutopilotPolicy.note(verdict) match
      case None =>
        val why = verdict match
          case Verdict.Leave(w) => w
          case _                => ""
        mgr.foreach { m =>
          val text = s"approval #${a.id} (${a.kind}) left for a person: $why"
          if !ManagerRepo.lastEventOf(ds, m.id, Feed.Approval).exists(_.text == text) then
            ManagerRepo.event(ds, m.id, run.flatMap(_.managerItem), a.runId, Feed.Approval, text)
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
            val text = s"$what ${if decision == Decision.Approve then "approved" else "rejected"} by the autopilot's checks: " +
              note.stripPrefix(AutopilotPolicy.NotePrefix).trim + (if d.state == ApprovalState.Failed.wire then s" — applying it failed: ${d.result.getOrElse("")}" else "")
            mgr.foreach(m => ManagerRepo.event(ds, m.id, run.flatMap(_.managerItem), a.runId, Feed.Approval, text.take(2000)))
            val _ = h.ops.queueAdd("log", QueueAdd(s"autopilot: $text".take(1000)))
            scribe.info(s"autopilot: $text")
            List(s"$what: ${decision.wire}")

  /** The checks of one approval as the corpus is now. */
  def judge(a: ApprovalInfo, run: Option[RunInfo], mgr: Option[Managers.ManagerInfo], now: Instant): Verdict =
    val day = ManagerPolicy.dayStart(now)
    val caps = cfg.approvals.auto
    a.approvalKind match
      case Some(ApprovalKind.ArticlePlan) =>
        a.articles match
          case None => Verdict.Leave("the approval carries no readable plan")
          case Some(plan) =>
            val goal = a.subject.getOrElse(plan.goal)
            val min = mgr.flatMap(_.config.minSources).orElse(run.flatMap(_.params.minSources)).getOrElse(cfg.seed.minSources)
            val facts = plan.articles.map { p =>
              val free = !AgentRepo.slugExists(ds, p.slug) && h.getArticle(p.slug).isLeft
              PlannedFacts(p.slug, free, h.filterReach(p.filter, Nil, 0)._1)
            }
            val today = AgentRepo.autoApprovedSince(ds, ApprovalKind.ArticlePlan, day)
              .filter(_.subject.contains(goal)).map(_.articles.fold(0)(_.articles.size)).sum
            AutopilotPolicy.articlePlan(facts, math.max(1, min), today, caps.articlesPerGoalPerDay)
      case Some(ApprovalKind.ArticlePublish) =>
        (a.article, run) match
          case (Some(d), Some(r)) =>
            val snap  = checkpoint[CompileArticlePipeline.Snap](r.id, "snapshot")
            val found = checkpoint[CompileArticlePipeline.Found](r.id, "filter")
            val facts = DraftFacts(d.slug, d.mode == DraftMode.Create, snap.exists(_.filter.isDefined), found.map(_.total),
              d.problems, d.cited.distinct.size, !AgentRepo.slugExists(ds, d.slug))
            AutopilotPolicy.articlePublish(facts, caps.publishMinSources,
              AgentRepo.autoApprovedSince(ds, ApprovalKind.ArticlePublish, day).size, caps.publishPerDay)
          case (None, _) => Verdict.Leave("the approval carries no readable draft")
          case (_, None) => Verdict.Leave("the run that drafted it is gone")
      case Some(ApprovalKind.GoalPlan) =>
        a.plan match
          case None => Verdict.Leave("the approval carries no readable plan")
          case Some(p) =>
            val goal = a.subject.getOrElse(p.goal)
            val depth = AutopilotPolicy.depth(goal, s => GoalRepo.get(ds, s).flatMap(_.parentSlug))
            AutopilotPolicy.goalPlan(GoalPlanFacts(goal, p.subGoals.size, p.queries.size, p.works.size, depth), caps.maxGoalDepth,
              AgentRepo.autoApprovedSince(ds, ApprovalKind.GoalPlan, day).size, caps.goalPlansPerDay) match
              // the focus: decided by code only for an active goal with few sub-goals open
              case v @ Verdict.Approve(_) => agents.focus.goalPlanBlock(goal, p.subGoals.size).fold(v)(Verdict.Leave(_))
              case v                      => v
      case _ => Verdict.Leave("not a kind the autopilot decides")

  private def checkpoint[A: io.circe.Decoder](run: String, step: String): Option[A] =
    AgentRepo.steps(ds, run, 100000).filter(s => s.name == step && s.status == StepStatus.Done.wire).lastOption
      .flatMap(_.output).flatMap(o => io.circe.parser.decode[A](o).toOption)
