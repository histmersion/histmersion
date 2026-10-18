package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.{ApprovalKind, Pipeline}
import com.histmersion.core.ArticlePlans.ArticlePlan
import com.histmersion.core.GoalPlans.LinkKind
import com.histmersion.core.PlanArticles.ArticlesAnswer
import com.histmersion.db.{AgentRepo, GoalLinkRepo, ManagerRepo}
import com.histmersion.server.Handlers
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.syntax.*

import javax.sql.DataSource

/** `propose-articles <goal>` (docs/research-process-managers.md §4 (b), phase 2): the seeding
  * manager's planner step. A model proposes the articles a research area should have; CODE keeps the
  * ones that can be compiled and are new ([[PlanArticles.verify]]: the slug free, the filter
  * returning at least `minSources` sources, less than `maxOverlap` of them shared with an article of
  * the area — linked to the goal, or already planned by its seeding manager); the plan becomes an
  * `article-plan` approval a person ALWAYS decides (no config makes it automatic). The run does not wait
  * for it: approving it lets the seeding manager compile the articles, one compile-article run each.
  *
  * Steps: load · propose (LLM, `article-planner`: `search`, `facets`, `get_article`, `article_coverage`,
  * `get_source`, read only; a plan with nothing left after the checks goes back with why) · record (the
  * approval) · close (the worklog line). A DRY run records the approval it would open on its step. */
final class ProposeArticlesPipeline(h: Handlers, ds: DataSource, cfg: => AgentsConfig) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.ProposeArticles
  import ProposeArticlesPipeline.*

  def run(ctx: RunCtx): Either[Halt, String] =
    val goal = ctx.run.target
    for
      loaded   <- ctx.code[Loaded]("load")(load(goal))
      plan     <- ctx.llm[ArticlesAnswer, ArticlePlan]("propose", StepAgent.ArticlePlanner, input(ctx, loaded), AnswerSchemas.articles)(
                    (a, last) => check(ctx, loaded, a, last).map(Checked(_, Nil)))
      recorded <- ctx.code[Recorded]("record")(record(ctx, goal, plan))
      closed   <- ctx.code[String]("close")(Pipelines.log(h, ctx, PlanArticles.logLine(goal, plan, recorded.line, ctx.run.id)))
    yield closed

  private def limits(ctx: RunCtx): PlanArticles.Limits =
    val p = ctx.run.params
    PlanArticles.Limits(math.max(1, p.minSources.getOrElse(cfg.seed.minSources)),
      math.min(1.0, math.max(0.0, cfg.seed.maxOverlap)), math.max(1, p.maxArticles.getOrElse(cfg.seed.maxArticles)))

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  private def load(slug: String): Either[String, Loaded] =
    h.getGoal(slug).left.map(_.message).map { g =>
      val links = GoalLinkRepo.links(ds, g.slug)
      val articles = links.filter(_.kind == LinkKind.Article).flatMap { l =>
        h.getArticle(l.ref).toOption.map { v =>
          val f = com.histmersion.db.ArticleRepo.load(ds, v.article.slug).flatMap(_.filter)
          Existing(v.article.slug, v.article.title, f, h.citedSources(v.article.body))
        }
      }
      // what its seeding manager will compile already counts as the area's
      val planned = ManagerRepo.items(ds, Managers.seedId(g.slug), None, 0, 1000)._1
        .filter(i => i.kind == Managers.ArticleCompile && !Managers.ItemStatus.parse(i.status).exists(s => s.isFinal && s != Managers.ItemStatus.Done))
        .flatMap(i => i.state.spec.map(sp => Existing(i.target, sp.title, Some(sp.filter), Nil)))
      val vocab = (field: String) =>
        h.facets(field, None, Filter()).toOption.toList.flatten.sortBy(-_.count).take(VocabularySize).map(_.value)
      Loaded(g.slug, g.title, g.notes, g.tags, g.regions, g.periodStart, g.periodEnd,
        (articles ++ planned.filterNot(p => articles.exists(_.slug == p.slug))),
        links.filter(_.kind == LinkKind.Query).map(_.ref), vocab("topics"), vocab("tags"))
    }

  private def input(ctx: RunCtx, l: Loaded): String =
    val lim = limits(ctx)
    val period = (l.periodStart, l.periodEnd) match
      case (Some(a), Some(b)) => s"$a to $b (signed years, BCE negative)"
      case _                  => "not given"
    def list(xs: List[String]) = if xs.isEmpty then "none" else xs.map(x => s"- $x").mkString("\n", "\n", "")
    val area = s"slug: ${l.slug}\ntitle: ${l.title}\nnotes: ${l.notes.getOrElse("none")}\ntags: ${l.tags.mkString(", ")}\n" +
      s"regions: ${l.regions.mkString(", ")}\nperiod: $period"
    s"Plan the articles of this research area.\n\n${Prompt.data("research-area", area)}\n\n" +
      s"Its articles (and the ones already planned): ${list(l.articles.map(a => s"[[${a.slug}]] — ${a.title}" +
        a.filter.fold(" (no filter)")(f => s" · ${f.line}")))}\n\nIts search queries: ${list(l.queries)}\n\n" +
      s"The corpus's most used topics: ${l.topics.mkString(", ")}\nIts most used tags: ${l.tags0.mkString(", ")}\n\n" +
      f"Limits: at most ${lim.maxArticles} articles; each filter returns at least ${lim.minSources} sources, and less than " +
      f"${lim.maxOverlap * 100}%.0f%% of them may belong to an article the area already has." + ctx.notes

  /** The source set of an existing article: its filter's sources and the ones it cites. */
  private def sourcesOf(e: Existing): Set[String] =
    e.filter.fold(Set.empty[String])(f => h.filterReach(f, Nil, OverlapCap)._3.toSet) ++ e.cited

  private def check(ctx: RunCtx, l: Loaded, a: ArticlesAnswer, last: Boolean): Either[List[String], ArticlePlan] =
    val lim = limits(ctx)
    PlanArticles.check(a, lim.maxArticles, last).flatMap { (cs, dropped) =>
      val taken = l.articles.map(_.slug).toSet
      val existing = l.articles.map(e => (e.slug, sourcesOf(e)))
      val plan = PlanArticles.verify(l.slug, a.rationale, cs, dropped,
        s => !taken(s) && !AgentRepo.slugExists(ds, s) && h.getArticle(s).isLeft,
        f => { val (n, _, rows) = h.filterReach(f, Nil, OverlapCap); (n, rows.toSet) }, existing, lim)
      if plan.isEmpty && !last then Left(plan.dropped.take(12) :+ "no proposed article passed the checks: fix the ones above or propose others")
      else Right(plan)
    }

  /** The plan as an `article-plan` approval (always a person's decision), through the write gate. */
  private def record(ctx: RunCtx, goal: String, plan: ArticlePlan): Either[String, Recorded] =
    if plan.isEmpty then Right(Recorded(None, s"nothing survived the checks; no approval was opened"))
    else
      ctx.write(s"${ApprovalKind.ArticlePlan.wire} approval for goal $goal: ${Plural.count(plan.articles.size, "article")}",
          plan.asJson)(
        Right(AgentRepo.openSubject(ds, Some(ctx.run.id), ApprovalKind.ArticlePlan, goal, plan.asJson.noSpaces))
          .map(a => Recorded(Some(a.id), s"approval ${a.id} waits for a person")))(
        Right(Recorded(None, s"no approval opened (${AgentsMarkdown.DryRunNote})")))

object ProposeArticlesPipeline:
  /** How many of the most used topics and tags the planner is shown; how many sources of a filter an
    * overlap is measured on. */
  val VocabularySize = 40
  val OverlapCap     = 1000

  /** An article of the area (linked to the goal, or planned by its seeding manager). */
  final case class Existing(slug: String, title: String, filter: Option[ArticleFilter], cited: List[String])
      derives KindlingsCodecAsObject

  /** The area as the planner sees it (checkpointed). `tags0` = the corpus's tags. */
  final case class Loaded(slug: String, title: String, notes: Option[String], tags: List[String], regions: List[String],
      periodStart: Option[Long], periodEnd: Option[Long], articles: List[Existing], queries: List[String],
      topics: List[String], tags0: List[String]) derives KindlingsCodecAsObject

  final case class Recorded(approval: Option[Long], line: String) derives KindlingsCodecAsObject
