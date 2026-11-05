package com.histmersion.domain.research.workflows

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
import com.histmersion.api.automation.Agents.{ApprovalKind, Pipeline}
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

  def run(ctx: RunCtx): Either[Halt, String] =
    val goal = ctx.run.target
    for
      loaded <- ctx.code[Loaded]("load")(load(goal))
      plan <- ctx.llm[ArticlesAnswer, ArticlePlan](
        "propose",
        StepAgent.ArticlePlanner,
        input(ctx, loaded),
        ResearchAnswers.articles
      )((a, last) => check(ctx, loaded, a, last).map(Checked(_, Nil)))
      recorded <- ctx.code[Recorded]("record")(record(ctx, goal, plan))
      closed <- ctx.code[String]("close")(
        Pipelines.log(site.workLists, ctx, PlanArticles.logLine(goal, plan, recorded.line, ctx.run.id))
      )
    yield closed

  private def limits(ctx: RunCtx): PlanArticles.Limits =
    val p = ctx.run.params
    PlanArticles.Limits(
      math.max(1, p.minSources.getOrElse(lib.seed.minSources)),
      math.min(1.0, math.max(0.0, lib.seed.maxOverlap)),
      math.max(1, p.maxArticles.getOrElse(lib.seed.maxArticles))
    )

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  /** The goals above `slug` and below it: an area's articles overlap its parent's and its sub-areas' as much as its own
    * (the autopilot seeds them all).
    */
  private def family(slug: String): List[String] =
    val up = Iterator
      .iterate(goals.get(slug).flatMap(_.parentSlug))(
        _.flatMap(p => goals.get(p).flatMap(_.parentSlug))
      )
      .takeWhile(_.isDefined)
      .take(16)
      .flatten
      .toList
    def down(s: String, depth: Int): List[String] =
      if depth > 16 then Nil else goals.children(s).flatMap(c => c.slug :: down(c.slug, depth + 1))
    (up ++ down(slug, 0)).distinct.filterNot(_ == slug)

  private def load(slug: String): Either[String, Loaded] =
    site.goal(slug).left.map(_.message).map { g =>
      val links = goals.links(g.slug)
      val area = g.slug :: family(g.slug)
      val articles = area.flatMap(a => goals.refs(a, com.histmersion.domain.automation.Goal.LinkKind.Article)).distinct.flatMap { ref =>
        site.articles.get(ref).toOption.map { v =>
          val f = site.articleStore.load(v.article.slug).flatMap(_.filter)
          Existing(v.article.slug, v.article.title, f, site.articles.citedSources(v.article.body))
        }
      }
      // what its seeding manager (or a parent's or sub-area's) will compile already counts as the area's
      val planned = area
        .flatMap(a => managers.items(Managers.seedId(a), 1000))
        .filter(i =>
          i.kind == Managers.ArticleCompile && !Managers.ItemStatus
            .parse(i.status)
            .exists(s => s.isFinal && s != Managers.ItemStatus.Done)
        )
        .flatMap(i => i.state.spec.map(sp => Existing(i.target, sp.title, Some(sp.filter), Nil)))
        .foldLeft(List.empty[Existing])((acc, e) => if acc.exists(_.slug == e.slug) then acc else acc :+ e)
      val vocab = (field: String) =>
        site.labels
          .facets(field, None, Filter())
          .toOption
          .toList
          .flatten
          .sortBy(-_.count)
          .take(VocabularySize)
          .map(_.value)
      Loaded(
        g.slug,
        g.title,
        g.notes,
        g.tags,
        g.regions,
        g.periodStart,
        g.periodEnd,
        (articles ++ planned.filterNot(p => articles.exists(_.slug == p.slug))),
        links.filter(_.kind == LinkKind.Query).map(_.ref),
        vocab("topics"),
        vocab("tags")
      )
    }

  private def input(ctx: RunCtx, l: Loaded): String =
    val lim = limits(ctx)
    val period = (l.periodStart, l.periodEnd) match
      case (Some(a), Some(b)) => s"$a to $b (signed years, BCE negative)"
      case _                  => "not given"
    def list(xs: List[String]) = if xs.isEmpty then "none" else xs.map(x => s"- $x").mkString("\n", "\n", "")
    val area =
      s"slug: ${l.slug}\ntitle: ${l.title}\nnotes: ${l.notes.getOrElse("none")}\ntags: ${l.tags.mkString(", ")}\n" +
        s"regions: ${l.regions.mkString(", ")}\nperiod: $period"
    s"Plan the articles of this research area.\n\n${Prompt.data("research-area", area)}\n\n" +
      s"Its articles, its parent's and its sub-areas' (and the ones already planned): ${list(
          l.articles.map(a =>
            s"[[${a.slug}]] — ${a.title}" +
              a.filter.fold(" (no filter)")(f => s" · ${f.line}")
          )
        )}\n\nIts search queries: ${list(l.queries)}\n\n" +
      s"The corpus's most used topics: ${l.topics.mkString(", ")}\nIts most used tags: ${l.tags0.mkString(", ")}\n\n" +
      f"Limits: at most ${lim.maxArticles} articles; each filter returns at least ${lim.minSources} sources, and less than " +
      f"${lim.maxOverlap * 100}%.0f%% of them may belong to an article the area already has." + ctx.notes

  /** The source set of an existing article: its filter's sources and the ones it cites. */
  private def sourcesOf(e: Existing): Set[String] =
    e.filter.fold(Set.empty[String])(f => site.articles.filterReach(f, Nil, OverlapCap)._3.toSet) ++ e.cited

  private def check(ctx: RunCtx, l: Loaded, a: ArticlesAnswer, last: Boolean): Either[List[String], ArticlePlan] =
    val lim = limits(ctx)
    PlanArticles.check(a, lim.maxArticles, last).flatMap { (cs, dropped) =>
      val taken = l.articles.map(_.slug).toSet
      val existing = l.articles.map(e => (e.slug, sourcesOf(e)))
      val plan = PlanArticles.verify(
        l.slug,
        a.rationale,
        cs,
        dropped,
        s => !taken(s) && !site.articleStore.exists(s) && site.articles.get(s).isLeft,
        f => { val (n, _, rows) = site.articles.filterReach(f, Nil, OverlapCap); (n, rows.toSet) },
        existing,
        lim
      )
      if plan.isEmpty && !last then
        Left(plan.dropped.take(12) :+ "no proposed article passed the checks: fix the ones above or propose others")
      else Right(plan)
    }

  /** The plan as an `article-plan` approval (a person's, or the supervisor's checks — [[AutoApprovals]]), through the
    * write gate.
    */
  private def record(ctx: RunCtx, goal: String, plan: ArticlePlan): Either[String, Recorded] =
    if plan.isEmpty then Right(Recorded(None, s"nothing survived the checks; no approval was opened"))
    else
      ctx.write(
        s"${ApprovalKind.ArticlePlan.wire} approval for goal $goal: ${Plural.count(plan.articles.size, "article")}",
        plan.asJson
      )(
        Right(ctx.approvalStore.openSubject(Some(ctx.run.id), ApprovalKind.ArticlePlan, goal, plan.asJson.noSpaces))
          .map(a => Recorded(Some(a.id), s"approval ${a.id} waits for a person"))
      )(Right(Recorded(None, s"no approval opened (${AgentsMarkdown.DryRunNote})")))

object ProposeArticlesPipeline:
  /** How many of the most used topics and tags the planner is shown; how many sources of a filter an overlap is
    * measured on.
    */
  val VocabularySize = 40
  val OverlapCap = 1000

  /** An article of the area (linked to the goal, or planned by its seeding manager). */
  final case class Existing(slug: Existing.Slug, title: Existing.Title, filter: Option[Article.Filter], cited: List[Existing.Cited])
      derives KindlingsCodecAsObject

  object Existing:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cited = Cited.T
    object Cited extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The area as the planner sees it (checkpointed). `tags0` = the corpus's tags. */
  final case class Loaded(
      slug: Loaded.Slug,
      title: Loaded.Title,
      notes: Option[Loaded.Notes],
      tags: List[Loaded.Tags],
      regions: List[Loaded.Regions],
      periodStart: Option[com.histmersion.Year],
      periodEnd: Option[com.histmersion.Year],
      articles: List[Existing],
      queries: List[Loaded.Queries],
      topics: List[Loaded.Topics],
      tags0: List[Loaded.Tags0]
  ) derives KindlingsCodecAsObject

  object Loaded:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Notes = Notes.T
    object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regions = Regions.T
    object Regions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Queries = Queries.T
    object Queries extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags0 = Tags0.T
    object Tags0 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Recorded(approval: Option[Recorded.Approval], line: Recorded.Line) derives KindlingsCodecAsObject

  object Recorded:
    type Approval = Approval.T
    object Approval extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Line = Line.T
    object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
