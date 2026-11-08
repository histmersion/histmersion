package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.agents.AnswerSchemas
import com.histmersion.domain.automation.agents.Checked
import com.histmersion.domain.automation.agents.Halt
import com.histmersion.domain.automation.agents.Prompt
import com.histmersion.domain.automation.agents.RunCtx
import com.histmersion.domain.automation.agents.StepAgent
import com.histmersion.domain.automation.approvals.AutoApprovals
import com.histmersion.domain.automation.workflows.PipelineDef
import com.histmersion.domain.automation.workflows.Pipelines

import com.histmersion.api.Plural
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.Agents.{ApprovalKind, Pipeline}
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.automation.agents.ApprovalService
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.research.Slug
import com.histmersion.domain.research.articles.ArticlesControllers
import com.histmersion.api.library.Facet
import com.histmersion.api.research.Article.Plans.ArticlePlan
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.domain.PlanArticles.ArticlesAnswer
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.automation.managers.ManagerService
import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.syntax.*
import com.histmersion.api.automation.{AgentsMarkdown, Managers}
import com.histmersion.api.library.Filter
import com.histmersion.api.research.Article
import com.histmersion.domain.Title
import com.histmersion.domain.library.Citekey
import com.histmersion.util.jobs.Job
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

/** `propose-articles <goal>` (docs/research-process-managers.md §4 (b), phase 2): the seeding manager's planner step. A
  * model proposes the articles a research area should have; CODE keeps the ones that can be compiled and are new
  * ([[PlanArticles.verify]]: the slug free, the filter returning at least `minSources` sources, less than `maxOverlap`
  * of them shared with an article of the area — linked to the goal, its parent goals or its sub-goals, or already
  * planned by one of their seeding managers); the plan becomes an `article-plan` approval — a person's, unless
  * `agents.approvals.article-plan = auto` (or the manager's `autoApprove`) lets the managers' supervisor decide it by
  * code checks ([[AutoApprovals]]). The run does not wait for it: approving it lets the seeding manager compile the
  * articles, one compile-article run each.
  *
  * Steps: load · propose (LLM, `article-planner`: `search`, `facets`, `get_article`, `article_coverage`, `get_source`,
  * read only; a plan with nothing left after the checks goes back with why) · record (the approval) · close (the
  * worklog line). A DRY run records the approval it would open on its step.
  */
final class ProposeArticlesPipeline(
    site: ResearchSite,
    goals: GoalService,
    managers: ManagerService,
    lib: => LibraryAgentsConfig
) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.ProposeArticles
  import ProposeArticlesPipeline.*

  def run(ctx: RunCtx): Either[Halt, PipelineDef.Summary] =
    Goal.Slug.either(ctx.run.target.value).left.map(m => Halt.Fail(Halt.Fail.Message(m))).flatMap(runFor(ctx, _))

  private def runFor(ctx: RunCtx, goal: Goal.Slug): Either[Halt, PipelineDef.Summary] =
    val runId = ctx.runId
    for
      loaded <- ctx.code[Loaded](AgentRepo.StepRecord.Name("load"))(load(goal))
      plan <- ctx.llm[ArticlesAnswer, ArticlePlan](
        AgentRepo.StepRecord.Name("propose"),
        StepAgent.ArticlePlanner,
        Prompt.UserText(input(ctx, loaded)),
        ResearchAnswers.articles
      )((a, last) => check(ctx, loaded, a, last.value).map(Checked(_, Nil)))
      recorded <- ctx.code[Recorded](AgentRepo.StepRecord.Name("record"))(record(ctx, goal, plan))
      closed <- ctx.code[String](AgentRepo.StepRecord.Name("close"))(
        log(ctx, PlanArticles.logLine(goal, plan, PlanArticles.Outcome(recorded.line.value), runId))
      )
    yield PipelineDef.Summary(closed)

  private def log(ctx: RunCtx, line: PlanArticles.LogLine): Either[String, String] =
    Pipelines.log(site.workLists, ctx, Pipelines.LogLine(line.value)).left.map(_.value).map(_.value)

  private def limits(ctx: RunCtx): PlanArticles.Limits =
    val p = ctx.run.params
    PlanArticles.Limits(
      PlanArticles.Limits.MinSources(math.max(1, p.minSources.fold(lib.seed.minSources.value)(_.value))),
      PlanArticles.Limits.MaxOverlap(math.min(1.0, math.max(0.0, lib.seed.maxOverlap.value))),
      PlanArticles.Limits.MaxArticles(math.max(1, p.maxArticles.fold(lib.seed.maxArticles.value)(_.value)))
    )

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  /** The goals above `slug` and below it: an area's articles overlap its parent's and its sub-areas' as much as its own
    * (the autopilot seeds them all).
    */
  private def family(slug: Goal.Slug): List[Goal.Slug] =
    val up = Iterator
      .iterate(goals.get(slug).flatMap(_.parentSlug))(
        _.flatMap(p => goals.get(Goal.Slug.assume(p.value)).flatMap(_.parentSlug))
      )
      .takeWhile(_.isDefined)
      .take(16)
      .flatten
      .toList
      .map(p => Goal.Slug.assume(p.value))
    def down(s: Goal.Slug, depth: Int): List[Goal.Slug] =
      if depth > 16 then Nil
      else
        goals.children(s).flatMap { c =>
          val child = Goal.Slug.assume(c.slug.value)
          child :: down(child, depth + 1)
        }
    (up ++ down(slug, 0)).distinct.filterNot(_ == slug)

  private def load(slug: Goal.Slug): Either[String, Loaded] =
    site.goal(slug).left.map(_.message.value).map { g =>
      val goal = Goal.Slug.assume(g.slug.value)
      val links = goals.links(goal)
      val area = goal :: family(goal)
      val articles = area.flatMap(a => goals.refs(a, Goal.LinkKind.Article)).distinct.flatMap { ref =>
        Slug.parse(ref.value).toOption.flatMap(site.articles.get(_).toOption).map { v =>
          val articleSlug = Slug.assume(v.article.slug.value)
          val f = site.articleStore.load(articleSlug).flatMap(_.filter)
          Existing(articleSlug, Title(v.article.title.value), f, site.articles.citedSources(v.article.body))
        }
      }
      // what its seeding manager (or a parent's or sub-area's) will compile already counts as the area's
      val planned = area
        .flatMap(a =>
          managers.items(
            Managers.seedId(com.histmersion.api.automation.Goal.Ref(a.value)),
            PageSize(1000)
          )
        )
        .filter(i =>
          i.kind.value == Managers.ArticleCompile && !Managers.ItemStatus
            .parse(i.status)
            .exists(s => s.isFinal && s != Managers.ItemStatus.Done)
        )
        .flatMap(i =>
          i.state.spec.map(sp => Existing(Slug.assume(i.target.value), Title(sp.title.value), Some(sp.filter), Nil))
        )
        .foldLeft(List.empty[Existing])((acc, e) => if acc.exists(_.slug == e.slug) then acc else acc :+ e)
      val vocab = (field: String) =>
        site.labels
          .facets(Facet.Field.Wire(field), None, Filter())
          .toOption
          .toList
          .flatten
          .sortBy(f => -f.count.value)
          .take(VocabularySize)
          .map(_.value.value)
      Loaded(
        goal,
        Title(g.title.value),
        g.notes.map(n => Loaded.Notes(n.value)),
        g.tags.map(t => Loaded.Tags(t.value)),
        g.regions.map(r => Loaded.Regions(r.value)),
        g.periodStart,
        g.periodEnd,
        (articles ++ planned.filterNot(p => articles.exists(_.slug == p.slug))),
        links.filter(_.kind == LinkKind.Query).map(l => Loaded.Queries(l.ref.value)),
        vocab("topics").map(Loaded.Topics(_)),
        vocab("tags").map(Loaded.Tags(_))
      )
    }

  private def input(ctx: RunCtx, l: Loaded): String =
    val lim = limits(ctx)
    val period = (l.periodStart, l.periodEnd) match
      case (Some(a), Some(b)) => s"$a to $b (signed years, BCE negative)"
      case _                  => "not given"
    def list(xs: List[String]) = if xs.isEmpty then "none" else xs.map(x => s"- $x").mkString("\n", "\n", "")
    val area =
      s"slug: ${l.slug}\ntitle: ${l.title}\nnotes: ${l.notes.fold("none")(_.value)}\ntags: ${l.tags.mkString(", ")}\n" +
        s"regions: ${l.regions.mkString(", ")}\nperiod: $period"
    s"Plan the articles of this research area.\n\n${Prompt.data("research-area", area)}\n\n" +
      s"Its articles, its parent's and its sub-areas' (and the ones already planned): ${list(
          l.articles.map(a =>
            s"[[${a.slug}]] — ${a.title}" +
              a.filter.fold(" (no filter)")(f => s" · ${f.line}")
          )
        )}\n\nIts search queries: ${list(l.queries.map(_.value))}\n\n" +
      s"The corpus's most used topics: ${l.topics.mkString(", ")}\nIts most used tags: ${l.tags0.mkString(", ")}\n\n" +
      f"Limits: at most ${lim.maxArticles} articles; each filter returns at least ${lim.minSources} sources, and less than " +
      f"${lim.maxOverlap.value * 100}%.0f%% of them may belong to an article the area already has." + ctx.notes

  /** The source set of an existing article: its filter's sources and the ones it cites. */
  private def sourcesOf(e: Existing): Set[Citekey] =
    e.filter.fold(Set.empty[Citekey])(f =>
      site.articles.filterReach(f, Nil, OverlapCap)._3.map(r => Citekey.assume(r.value)).toSet
    ) ++ e.cited

  private def check(
      ctx: RunCtx,
      l: Loaded,
      a: ArticlesAnswer,
      last: Boolean
  ): Either[List[Problem], ArticlePlan] =
    val lim = limits(ctx)
    PlanArticles
      .check(a, lim.maxArticles, PlanArticles.LastRound(last))
      .left
      .map(_.map(p => Problem(p.value)))
      .flatMap { (cs, dropped) =>
        val taken = l.articles.map(_.slug).toSet
        val existing = l.articles.map(e => (Article.Slug(e.slug.value), sourcesOf(e)))
        val plan = PlanArticles.verify(
          l.slug,
          a.rationale,
          cs,
          dropped,
          s =>
            Slug
              .parse(s.value)
              .fold(_ => true, d => !taken(d) && !site.articleStore.exists(d) && site.articles.get(d).isLeft),
          f => {
            val (n, _, rows) = site.articles.filterReach(f, Nil, OverlapCap)
            (
              Article.Plans.ProposedArticle.Hits(n.value),
              rows.map(r => Citekey.assume(r.value)).toSet
            )
          },
          existing,
          lim
        )
        if plan.isEmpty && !last then
          Left(
            plan.dropped.take(12).map(d => Problem(d.value)) :+
              Problem("no proposed article passed the checks: fix the ones above or propose others")
          )
        else Right(plan)
      }

  /** The plan as an `article-plan` approval (a person's, or the supervisor's checks — [[AutoApprovals]]), through the
    * write gate.
    */
  private def record(ctx: RunCtx, goal: Goal.Slug, plan: ArticlePlan): Either[String, Recorded] =
    if plan.isEmpty then Right(Recorded(None, Recorded.Line("nothing survived the checks; no approval was opened")))
    else
      ctx.write(
        s"${ApprovalKind.ArticlePlan.wire} approval for goal $goal: ${Plural.count(Plural.CountN(plan.articles.size.toLong), Plural.CountSingular("article"))}",
        plan.asJson
      )(
        Right(
          ctx.approvalStore.openSubject(
            Some(Job.Id.assume(ctx.run.id.value)),
            ApprovalKind.ArticlePlan,
            ApprovalQuery.Subject(goal.value),
            ApprovalService.Payload(plan.asJson.noSpaces)
          )
        ).map(a => Recorded(Some(a.id), Recorded.Line(s"approval ${a.id} waits for a person")))
      )(Right(Recorded(None, Recorded.Line(s"no approval opened (${AgentsMarkdown.DryRunNote})"))))

object ProposeArticlesPipeline:
  /** How many of the most used topics and tags the planner is shown; how many sources of a filter an overlap is
    * measured on.
    */
  val VocabularySize = 40
  val OverlapCap: ArticlesControllers.FilterReachCap = ArticlesControllers.FilterReachCap(1000)

  /** An article of the area (linked to the goal, or planned by its seeding manager). */
  final case class Existing(
      slug: Slug,
      title: Title,
      filter: Option[Article.Filter],
      cited: List[Citekey]
  ) derives KindlingsCodecAsObject

  /** The area as the planner sees it (checkpointed). `tags0` = the corpus's tags. */
  final case class Loaded(
      slug: Goal.Slug,
      title: Title,
      notes: Option[Loaded.Notes],
      tags: List[Loaded.Tags],
      regions: List[Loaded.Regions],
      periodStart: Option[Year],
      periodEnd: Option[Year],
      articles: List[Existing],
      queries: List[Loaded.Queries],
      topics: List[Loaded.Topics],
      tags0: List[Loaded.Tags]
  ) derives KindlingsCodecAsObject

  object Loaded:
    type Notes = Notes.T
    object Notes extends Newtype[String, Pure]
    type Tags = Tags.T
    object Tags extends Newtype[String, Pure]
    type Regions = Regions.T
    object Regions extends Newtype[String, Pure]
    type Queries = Queries.T
    object Queries extends Newtype[String, Pure]
    type Topics = Topics.T
    object Topics extends Newtype[String, Pure]

  final case class Recorded(approval: Option[Agents.ApprovalInfo.Id], line: Recorded.Line)
      derives KindlingsCodecAsObject

  object Recorded:
    type Line = Line.T
    object Line extends Newtype[String, Pure]
