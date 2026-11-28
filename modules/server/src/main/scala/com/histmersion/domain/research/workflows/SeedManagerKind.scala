package com.histmersion
package domain.research.workflows
import com.histmersion.domain.automation.workflows.InTreeWorkflow
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.approvals.ApprovalChecks
import com.histmersion.domain.automation.managers.ManagerContext
import com.histmersion.domain.automation.managers.ManagerKindDefinition
import com.histmersion.domain.automation.managers.ManagerLight
import com.histmersion.domain.automation.goals.BGoalLinkRow
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.research.Slug

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
import com.histmersion.domain.automation.Run
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.research.articles.ArticlesControllers
import com.histmersion.domain.ManagerPolicy.Item
import com.histmersion.util.database.WritePermit
import com.histmersion.util.database.Unused
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The `seed:<goal>` manager kind (propose-articles, then one article-compile item per article a person approved — a
  * compiled article is queued for the review manager — and the goal's plan), as research contributes it: over its
  * articles and the goals its site reads; the seeding settings are `agents.seed`.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.Repository, "pending G11 (a repository stays inside its feature)")
final class SeedManagerKind(site: ResearchSite, lib: => LibraryAgentsConfig) extends ManagerKindDefinition:
  val kind: Kind = Kind.Seed
  val creator: domain.Creator.New =
    domain.Creator.code(InTreeWorkflow.Research, domain.Creator.Workflow.Name("seed-manager"))

  def defaults(cfg: AgentsConfig, m: ManagerDefaults): ManagerConfig =
    SeedManagerKind.plan(lib.seed).managerDefaults(m)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pipelineOf(itemKind: ManagerContext.ItemKind): Pipeline = itemKind.value match
    case ProposeArticles => Pipeline.ProposeArticles
    case ArticleCompile  => Pipeline.CompileArticle
    case PlanGoalItem    => Pipeline.PlanGoal
    case _               => Pipeline.ReviewArticle

  /** A firing proposes the goal's next articles. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fire(c: ManagerContext, m: ManagerInfo, now: Instant)(using WritePermit): List[ManagerKindDefinition.FirePhrase] =
    m.goal.toList.map { g =>
      val (_, isNew) = c.upsert(
        ManagerLight.Id(m.id.value),
        ManagerContext.ItemKind(ProposeArticles),
        ManagerContext.ItemTarget(g.value),
        ItemOrigin.Cron,
        ManagerContext.Priority(0),
        reopen = ManagerContext.UpsertReopen.Reopen
      )
      ManagerKindDefinition.FirePhrase(s"$ProposeArticles ${if isNew then "added" else "due"}")
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def facts(c: ManagerContext, item: ManagerItem, run: Run.Id): StepFacts =
    lazy val progress = c.progress(run)
    item.kind.value match
      case ProposeArticles =>
        c.approvalFacts(
          run,
          Approval.Kind.ArticlePlan,
          Item.Outcome(s"no article proposed — $progress")
        )
      case ArticleCompile =>
        CompileArticlePipeline.written(c, run) match
          case Some(w) if w.written.written =>
            StepFacts.Finished(Item.Outcome(s"compiled (${w.why})"))
          case Some(w) =>
            // a draft the autopilot's checks rejected is compiled again (a new draft may pass them)
            c.approvalsOf(run, Approval.Kind.ArticlePublish)
              .lastOption
              .filter(a => a.state == ApprovalState.Rejected.wire && a.note.exists(_.byAutopilot)) match
              case Some(a) =>
                StepFacts.Redo(
                  Item.Outcome(
                    s"draft rejected by the autopilot's checks (approval #${a.id}: " +
                      a.note.fold("")(_.withoutAutopilotMark.value).take(300) + ")"
                  )
                )
              case None =>
                StepFacts.NotDone(Item.Outcome(s"stopped: no article written — ${w.why}"), None)
          case None => StepFacts.NotDone(Item.Outcome(s"stopped: nothing compiled — $progress"), None)
      case PlanGoalItem =>
        // a blocking approval: the run ends only once its plan was decided
        c.approvalsOf(run, Approval.Kind.GoalPlan).lastOption match
          case None    => StepFacts.Finished(Item.Outcome(s"nothing planned — $progress"))
          case Some(a) =>
            StepFacts.Finished(
              Item.Outcome(
                s"plan #${a.id} " + ManagerPolicy
                  .decided(
                    Approval.State.Wire(a.state.value),
                    a.result.map(_.value).orElse(a.note.map(_.value)).map(r => Approval.Outcome(r))
                  )
                  .fold(a.state.value)(_.value)
              )
            )
      case _ => StepFacts.Finished(Item.Outcome("enriched"))

  /** A compiled article is linked to its research area and queued for the review manager; an approved plan becomes
    * compile items; an approved goal plan may have created sub-goals.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  override def afterDone(
      c: ManagerContext,
      m: ManagerInfo,
      item: ManagerItem,
      run: Option[Run.Id],
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]
  )(using WritePermit)(using Creator.New): Unit =
    item.kind.value match
      case ArticleCompile if Slug.option(item.target.value).exists(site.articles.get(_).isRight) =>
        m.goal.foreach(g =>
          c.linkGoal(
            domain.automation.Goal.Slug.assume(g.value),
            Goal.Plans.LinkKind.Article,
            BGoalLinkRow.Ref(item.target.value),
            Some(BGoalLinkRow.Note(s"compiled by ${m.id}"))
          )
        )
        val review = ManagerLight.Id(ReviewId)
        Unused(c.ensure(Kind.Review, review, None), Unused.Why.Idempotent)
        c.chainReview(
          review,
          Slug.assume(item.target.value),
          Problem.Unclassified(Legacy.Text(s"compiled by ${m.id}")),
          out
        )
        c.event(
          ManagerLight.Id(m.id.value),
          Some(ManagerContext.ItemId(item.id.value)),
          run,
          ManagerContext.EventFeed(Feed.Evaluated),
          ManagerContext.EventText(s"${item.target}: queued for the review manager")
        )
      case ProposeArticles =>
        run.toList
          .flatMap(r => c.approvalsOf(r, Approval.Kind.ArticlePlan))
          .filter(_.state == ApprovalState.Approved.wire)
          .foreach(a => c.applyPlan(a, out): Unit)
      case PlanGoalItem =>
        // an approved plan may have created sub-goals: the autopilot gives them their managers next tick
        if run.toList
            .flatMap(r => c.approvalsOf(r, Approval.Kind.GoalPlan))
            .exists(_.state == ApprovalState.Approved.wire)
        then c.autopilotSoon()
      case _ => ()

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def target(
      m: ManagerInfo,
      itemKind: ManagerContext.ItemKind,
      raw: ManagerItem.Target
  ): Either[Api.Error, ManagerContext.ItemTarget] =
    itemKind.value match
      case ProposeArticles | PlanGoalItem =>
        val g = Option(raw.value.trim).filter(_.nonEmpty).orElse(m.goal.map(_.value)).getOrElse("")
        domain.automation.Goal.Slug
          .option(g)
          .toRight(Api.Error(Problem.NotFound.Entity(Problem.TypeName.of(Goal.Slug), Problem.Raw(s"$g"))))
          .flatMap(site.goal(_))
          .map(_.slug)
          .flatMap(s =>
            Either.cond(
              m.goal.forall(_ == s),
              ManagerContext.ItemTarget(s.value),
              Api.Error(
                Problem.Unclassified(
                  Legacy.Text(s"manager ${m.id} plans the articles of ${m.goal.fold("?")(_.value)}, not of $s")
                )
              )
            )
          )
      case ArticleCompile =>
        Left(
          Api.Error(
            Problem.Unclassified(
              Legacy.Text(s"'$ArticleCompile' items come from an approved article plan (propose-articles)")
            )
          )
        )
      case other => Left(Api.Error(Problem.Unclassified(Legacy.Text(s"unknown item kind '$other'"))))

  /** The autopilot's checks of the approvals its items wait for: an article plan (each article's slug free and its
    * filter reaching enough sources, within the area's daily cap) and an article to publish (its draft's filter, the
    * sources it cites, the daily cap).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
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
            case None       => Verdict.Leave(AutopilotPolicy.Rationale("the approval carries no readable plan"))
            case Some(plan) =>
              val goal = a.subject.map(_.value).getOrElse(plan.goal.value)
              val min =
                mgr
                  .flatMap(_.config.minSources)
                  .map(_.value)
                  .orElse(run.flatMap(_.params.minSources).map(_.value))
                  .getOrElse(lib.seed.minSources.value)
              val facts = plan.articles.map { p =>
                val free = !c.slugExists(ApprovalChecks.Slug(p.slug.value)) &&
                  Slug.parse(p.slug.value).fold(_ => true, s => site.articles.get(s).isLeft)
                PlannedFacts(
                  PlannedFacts.Slug(p.slug.value),
                  (if free then PlannedFacts.Availability.Free else PlannedFacts.Availability.Taken),
                  PlannedFacts.Hits(
                    site.articles.filterReach(p.filter, Nil, ArticlesControllers.FilterReachCap(0))._1.value
                  )
                )
              }
              val today = c
                .autoApprovedSince(Approval.Kind.ArticlePlan, day)
                .filter(_.subject.exists(_.value == goal))
                .map(_.articles.fold(0)(_.articles.size))
                .sum
              AutopilotPolicy.articlePlan(
                facts,
                AutopilotPolicy.MinSources(math.max(1, min)),
                AutopilotPolicy.ApprovedToday(today),
                AutopilotPolicy.Cap(caps.articlesPerGoalPerDay.value)
              )
        case Some(ApprovalKind.ArticlePublish) =>
          (a.article, run) match
            case (Some(d), Some(r)) =>
              val snap = c
                .checkpoint(Run.Id.assume(r.id.value), AgentRepo.StepRecord.Name("snapshot"))
                .flatMap(o => RawJson.decode[CompileArticlePipeline.Snap](o).toOption)
              val found = c
                .checkpoint(Run.Id.assume(r.id.value), AgentRepo.StepRecord.Name("filter"))
                .flatMap(o => RawJson.decode[CompileArticlePipeline.Found](o).toOption)
              val facts = DraftFacts(
                DraftFacts.Slug(d.slug.value),
                (if d.mode == DraftMode.Create then DraftFacts.Create.Create else DraftFacts.Create.Update),
                (if snap.exists(_.filter.isDefined) then DraftFacts.FilterRecorded.Recorded
                 else DraftFacts.FilterRecorded.Unrecorded),
                found.map(f => DraftFacts.Hits(f.total.value)),
                d.problems.map(p => DraftFacts.Problems(p.value)),
                DraftFacts.Cited(d.cited.distinct.size),
                (if !c.slugExists(ApprovalChecks.Slug(d.slug.value)) then DraftFacts.Availability.Free
                 else DraftFacts.Availability.Taken)
              )
              AutopilotPolicy.articlePublish(
                facts,
                AutopilotPolicy.MinSources(caps.publishMinSources.value),
                AutopilotPolicy.ApprovedToday(
                  c.autoApprovedSince(Approval.Kind.ArticlePublish, day).size
                ),
                AutopilotPolicy.Cap(c.publishCap.value)
              )
            case (None, _) => Verdict.Leave(AutopilotPolicy.Rationale("the approval carries no readable draft"))
            case (_, None) => Verdict.Leave(AutopilotPolicy.Rationale("the run that drafted it is gone"))
        case _ => Verdict.Leave(AutopilotPolicy.Rationale("not a kind the autopilot decides"))
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def dispatch(
      c: ManagerContext,
      m: ManagerInfo,
      s: ReviewSettings,
      item: ManagerItem,
      now: Instant,
      out: Builder[ManagerContext.TickLine, List[ManagerContext.TickLine]]
  )(using WritePermit)(using Creator.New): Option[ManagerLight.Id] =
    val goalOf = domain.automation.Goal.Slug.option(item.target.value).flatMap(site.goal(_).toOption)
    def budget = m.config.budgetUsd.map(b => RunRequest.BudgetUsd(b.value))
    item.kind.value match
      case ProposeArticles =>
        goalOf match
          case None =>
            c.settleNow(m, item, ItemStatus.Cancelled, ManagerContext.SettleNowWhy("the goal no longer exists"))
          case Some(g) =>
            c.start(
              m,
              item,
              RunRequest(
                Pipeline.ProposeArticles.wire,
                g.slug.asRunTarget,
                budgetUsd = budget
              ),
              _.modify(_.minSources)
                .setTo(m.config.minSources.map(v => v))
                .modify(_.maxArticles)
                .setTo(m.config.maxArticles.map(v => v)),
              item.state,
              ManagerContext.StartWhat("propose-articles"),
              out
            )
      case ArticleCompile =>
        item.state.spec match
          case None =>
            c.settleNow(m, item, ItemStatus.Cancelled, ManagerContext.SettleNowWhy("no approved article to compile"))
          case Some(_) if c.slugExists(Slug.assume(item.target.value)) =>
            c.settleNow(
              m,
              item,
              ItemStatus.Cancelled,
              ManagerContext.SettleNowWhy("an article with this slug was created meanwhile")
            )
          case Some(sp) =>
            c.start(
              m,
              item,
              RunRequest(
                Pipeline.CompileArticle.wire,
                RunRequest.Target(item.target.value),
                budgetUsd = budget,
                title = Some(RunRequest.Title(sp.title.value)),
                kind = Some(RunRequest.Kind(sp.kind.value)),
                query = sp.filter.query.map(q => RunRequest.Query(q.value)),
                topics = sp.filter.topics.map(t => RunRequest.Topics(t.value)),
                tags = sp.filter.tags.map(t => RunRequest.Tags(t.value)),
                goal = m.goal
              ),
              _.modify(_.chainReview).setTo(Some(RunParams.ChainReview.Alone)),
              item.state,
              ManagerContext.StartWhat("compile-article (new; its review follows as an item)"),
              out
            )
      case PlanGoalItem =>
        goalOf match
          case None =>
            c.settleNow(m, item, ItemStatus.Cancelled, ManagerContext.SettleNowWhy("the goal no longer exists"))
          case Some(g) =>
            // sub-goals only above the depth limit (a goal there gets queries and works, no children)
            val depth = AutopilotPolicy.depth(
              Slug.assume(g.slug.value),
              s => c.parentOf(domain.automation.Goal.Slug.assume(s.value)).map(p => Slug.assume(p.value))
            )
            val leaf = depth.value >= c.cfg.approvals.auto.maxGoalDepth.value
            c.start(
              m,
              item,
              RunRequest(
                Pipeline.PlanGoal.wire,
                g.slug.asRunTarget,
                budgetUsd = budget
              ),
              p => if leaf then p.modify(_.subGoals).setTo(Some(RunParams.SubGoals(0))) else p,
              item.state,
              ManagerContext.StartWhat(
                s"plan-goal (depth $depth${if leaf then ": queries and works, no sub-goals" else ""})"
              ),
              out
            )
      case other =>
        c.settleNow(
          m,
          item,
          ItemStatus.Cancelled,
          ManagerContext.SettleNowWhy(s"'$other' items are not driven by a ${m.kind} manager")
        )

object SeedManagerKind:
  /** The seeding defaults a seed manager's plan is held to. */

  def plan(seed: AgentSeedConfig): SeedPlanDefaults = seed.transformInto[SeedPlanDefaults]
