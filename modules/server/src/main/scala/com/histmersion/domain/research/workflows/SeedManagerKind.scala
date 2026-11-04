package com.histmersion.domain.research.workflows

import com.histmersion.domain.automation.approvals.ApprovalChecks
import com.histmersion.domain.automation.managers.ManagerContext
import com.histmersion.domain.automation.managers.ManagerKindDefinition

import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.AutopilotPolicy
import com.histmersion.domain.ManagerPolicy
import com.histmersion.domain.AutopilotPolicy.DraftFacts
import com.histmersion.domain.AutopilotPolicy.PlannedFacts
import com.histmersion.domain.AutopilotPolicy.Verdict
import com.histmersion.api.automation.Goal.Plans.DraftMode
import com.histmersion.domain.ManagerPolicy.ReviewSettings
import com.histmersion.domain.ManagerPolicy.StepFacts
import com.histmersion.config.{AgentsConfig, AgentSeedConfig, LibraryAgentsConfig}
import com.histmersion.domain.ManagerDefaults
import com.histmersion.domain.SeedPlanDefaults
import io.scalaland.chimney.dsl.*

import java.time.Instant
import scala.collection.mutable.Builder
import com.histmersion.api.Api
import com.histmersion.api.automation.Goal

/** The `seed:<goal>` manager kind (propose-articles, then one article-compile item per article a person approved — a
  * compiled article is queued for the review manager — and the goal's plan), as research contributes it: over its
  * articles and the goals its site reads; the seeding settings are `agents.seed`.
  */
final class SeedManagerKind(site: ResearchSite, lib: => LibraryAgentsConfig) extends ManagerKindDefinition:
  val kind: Kind = Kind.Seed

  def defaults(cfg: AgentsConfig, m: ManagerDefaults): ManagerConfig =
    SeedManagerKind.plan(lib.seed).managerDefaults(m)

  def pipelineOf(itemKind: String): Pipeline = itemKind match
    case ProposeArticles => Pipeline.ProposeArticles
    case ArticleCompile  => Pipeline.CompileArticle
    case PlanGoalItem    => Pipeline.PlanGoal
    case _               => Pipeline.ReviewArticle

  /** A firing proposes the goal's next articles. */
  def fire(c: ManagerContext, m: ManagerInfo, now: Instant): List[String] =
    m.goal.toList.map { g =>
      val (_, isNew) = c.upsert(m.id, ProposeArticles, g, ItemOrigin.Cron, 0, reopen = true)
      s"$ProposeArticles ${if isNew then "added" else "due"}"
    }

  def facts(c: ManagerContext, item: ManagerItem, run: String): StepFacts =
    lazy val progress = c.progress(run)
    item.kind match
      case ProposeArticles => c.approvalFacts(run, com.histmersion.domain.automation.Approval.Kind.ArticlePlan, s"no article proposed — $progress")
      case ArticleCompile  =>
        CompileArticlePipeline.written(c, run) match
          case Some(w) if w.written => StepFacts.Finished(s"compiled (${w.why})")
          case Some(w)              =>
            // a draft the autopilot's checks rejected is compiled again (a new draft may pass them)
            c.approvalsOf(run, com.histmersion.domain.automation.Approval.Kind.ArticlePublish)
              .lastOption
              .filter(a =>
                a.state == ApprovalState.Rejected.wire && a.note.exists(_.startsWith(AutopilotPolicy.NotePrefix))
              ) match
              case Some(a) =>
                StepFacts.Redo(
                  s"draft rejected by the autopilot's checks (approval #${a.id}: " +
                    a.note.getOrElse("").stripPrefix(AutopilotPolicy.NotePrefix).trim.take(300) + ")"
                )
              case None => StepFacts.NotDone(s"stopped: no article written — ${w.why}", None)
          case None => StepFacts.NotDone(s"stopped: nothing compiled — $progress", None)
      case PlanGoalItem =>
        // a blocking approval: the run ends only once its plan was decided
        c.approvalsOf(run, com.histmersion.domain.automation.Approval.Kind.GoalPlan).lastOption match
          case None    => StepFacts.Finished(s"nothing planned — $progress")
          case Some(a) =>
            StepFacts.Finished(
              s"plan #${a.id} " + ManagerPolicy.decided(a.state, a.result.orElse(a.note)).getOrElse(a.state)
            )
      case _ => StepFacts.Finished("enriched")

  /** A compiled article is linked to its research area and queued for the review manager; an approved plan becomes
    * compile items; an approved goal plan may have created sub-goals.
    */
  override def afterDone(
      c: ManagerContext,
      m: ManagerInfo,
      item: ManagerItem,
      run: Option[String],
      out: Builder[String, List[String]]
  ): Unit =
    item.kind match
      case ArticleCompile if site.articles.get(item.target).isRight =>
        m.goal.foreach(g => c.linkGoal(g, Goal.Plans.LinkKind.Article, item.target, Some(s"compiled by ${m.id}")))
        val _ = c.ensure(Kind.Review, ReviewId, None)
        c.chainReview(ReviewId, item.target, s"compiled by ${m.id}", out)
        c.event(m.id, Some(item.id), run, Feed.Evaluated, s"${item.target}: queued for the review manager")
      case ProposeArticles =>
        run.toList
          .flatMap(r => c.approvalsOf(r, com.histmersion.domain.automation.Approval.Kind.ArticlePlan))
          .filter(_.state == ApprovalState.Approved.wire)
          .foreach(a => c.applyPlan(a, out): Unit)
      case PlanGoalItem =>
        // an approved plan may have created sub-goals: the autopilot gives them their managers next tick
        if run.toList
            .flatMap(r => c.approvalsOf(r, com.histmersion.domain.automation.Approval.Kind.GoalPlan))
            .exists(_.state == ApprovalState.Approved.wire)
        then c.autopilotSoon()
      case _ => ()

  def target(m: ManagerInfo, itemKind: String, raw: String): Either[Api.Error, String] =
    itemKind match
      case ProposeArticles | PlanGoalItem =>
        val g = Option(raw.trim).filter(_.nonEmpty).orElse(m.goal).getOrElse("")
        site
          .goal(g)
          .map(_.slug)
          .flatMap(s =>
            Either.cond(
              m.goal.forall(_ == s),
              s,
              Api.Error(s"manager ${m.id} plans the articles of ${m.goal.getOrElse("?")}, not of $s")
            )
          )
      case ArticleCompile =>
        Left(Api.Error(s"'$ArticleCompile' items come from an approved article plan (propose-articles)"))
      case other => Left(Api.Error(s"unknown item kind '$other'"))

  /** The autopilot's checks of the approvals its items wait for: an article plan (each article's slug free and its
    * filter reaching enough sources, within the area's daily cap) and an article to publish (its draft's filter, the
    * sources it cites, the daily cap).
    */
  override def judge(
      c: ApprovalChecks,
      a: ApprovalInfo,
      run: Option[RunInfo],
      mgr: Option[ManagerInfo],
      now: Instant
  ): Option[Verdict] =
    val day = ManagerPolicy.dayStart(now)
    val caps = c.config.approvals.auto
    Option.when(a.approvalKind.exists(k => k == ApprovalKind.ArticlePlan || k == ApprovalKind.ArticlePublish)) {
      a.approvalKind match
        case Some(ApprovalKind.ArticlePlan) =>
          a.articles match
            case None       => Verdict.Leave("the approval carries no readable plan")
            case Some(plan) =>
              val goal = a.subject.getOrElse(plan.goal)
              val min =
                mgr
                  .flatMap(_.config.minSources)
                  .orElse(run.flatMap(_.params.minSources))
                  .getOrElse(lib.seed.minSources)
              val facts = plan.articles.map { p =>
                val free = !c.slugExists(p.slug) && site.articles.get(p.slug).isLeft
                PlannedFacts(p.slug, free, site.articles.filterReach(p.filter, Nil, 0)._1)
              }
              val today = c.autoApprovedSince(com.histmersion.domain.automation.Approval.Kind.ArticlePlan, day)
                .filter(_.subject.contains(goal))
                .map(_.articles.fold(0)(_.articles.size))
                .sum
              AutopilotPolicy.articlePlan(facts, math.max(1, min), today, caps.articlesPerGoalPerDay)
        case Some(ApprovalKind.ArticlePublish) =>
          (a.article, run) match
            case (Some(d), Some(r)) =>
              val snap = c
                .checkpoint(r.id, "snapshot")
                .flatMap(o => io.circe.parser.decode[CompileArticlePipeline.Snap](o).toOption)
              val found = c
                .checkpoint(r.id, "filter")
                .flatMap(o => io.circe.parser.decode[CompileArticlePipeline.Found](o).toOption)
              val facts = DraftFacts(
                d.slug,
                d.mode == DraftMode.Create,
                snap.exists(_.filter.isDefined),
                found.map(_.total),
                d.problems,
                d.cited.distinct.size,
                !c.slugExists(d.slug)
              )
              AutopilotPolicy.articlePublish(
                facts,
                caps.publishMinSources,
                c.autoApprovedSince(com.histmersion.domain.automation.Approval.Kind.ArticlePublish, day).size,
                c.publishCap
              )
            case (None, _) => Verdict.Leave("the approval carries no readable draft")
            case (_, None) => Verdict.Leave("the run that drafted it is gone")
        case _ => Verdict.Leave("not a kind the autopilot decides")
    }

  def dispatch(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      now: Instant,
      out: Builder[String, List[String]]
  ): Option[String] =
    item.kind match
      case ProposeArticles =>
        site.goal(item.target) match
          case Left(_)  => c.settleNow(m, item, ItemStatus.Cancelled, "the goal no longer exists")
          case Right(g) =>
            c.start(
              m,
              item,
              RunRequest(Pipeline.ProposeArticles.wire, g.slug, budgetUsd = m.config.budgetUsd),
              _.copy(minSources = m.config.minSources, maxArticles = m.config.maxArticles),
              item.state,
              "propose-articles",
              out
            )
      case ArticleCompile =>
        item.state.spec match
          case None => c.settleNow(m, item, ItemStatus.Cancelled, "no approved article to compile")
          case Some(_) if c.slugExists(item.target) =>
            c.settleNow(m, item, ItemStatus.Cancelled, "an article with this slug was created meanwhile")
          case Some(sp) =>
            c.start(
              m,
              item,
              RunRequest(
                Pipeline.CompileArticle.wire,
                item.target,
                budgetUsd = m.config.budgetUsd,
                title = Some(sp.title),
                kind = Some(sp.kind),
                query = sp.filter.query,
                topics = sp.filter.topics,
                tags = sp.filter.tags,
                goal = m.goal
              ),
              _.copy(chainReview = Some(false)),
              item.state,
              "compile-article (new; its review follows as an item)",
              out
            )
      case PlanGoalItem =>
        site.goal(item.target) match
          case Left(_)  => c.settleNow(m, item, ItemStatus.Cancelled, "the goal no longer exists")
          case Right(g) =>
            // sub-goals only above the depth limit (a goal there gets queries and works, no children)
            val depth = AutopilotPolicy.depth(g.slug, c.parentOf)
            val leaf = depth >= c.cfg.approvals.auto.maxGoalDepth
            c.start(
              m,
              item,
              RunRequest(Pipeline.PlanGoal.wire, g.slug, budgetUsd = m.config.budgetUsd),
              p => if leaf then p.copy(subGoals = Some(0)) else p,
              item.state,
              s"plan-goal (depth $depth${if leaf then ": queries and works, no sub-goals" else ""})",
              out
            )
      case other =>
        c.settleNow(m, item, ItemStatus.Cancelled, s"'$other' items are not driven by a ${m.kind} manager")

object SeedManagerKind:
  /** The seeding defaults a seed manager's plan is held to. */
  def plan(seed: AgentSeedConfig): SeedPlanDefaults = seed.transformInto[SeedPlanDefaults]
