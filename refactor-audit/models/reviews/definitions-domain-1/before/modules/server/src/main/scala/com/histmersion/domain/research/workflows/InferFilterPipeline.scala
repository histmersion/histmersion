package com.histmersion.domain.research.workflows

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.workflows.{PipelineDef, Pipelines}

import com.histmersion.api.Plural
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalMode, ApprovalState, Pipeline}
import com.histmersion.api.research.Article.Plans.FilterProposal
import com.histmersion.domain.FilterInference.{FilterAnswer, Inferred}
import com.histmersion.domain.research.articles.{ArticlesControllers, ArticleService}
import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.syntax.*
import com.histmersion.api.automation.AgentsMarkdown
import com.histmersion.api.library.Filter
import com.histmersion.api.research.Article

/** `infer-filter <article>` (docs/research-process-managers.md §4 (a), phase 2): an article that records no coverage
  * filter (#958) gets one proposed — the review manager runs it BEFORE the article's review, so the review's coverage
  * checks have a filter to read.
  *
  * Steps: load (the article, the sources it cites, the corpus vocabulary) · propose (LLM, the `filter-inferrer`
  * definition: `search`, `facets`, `get_article`, `get_source`, read only; code runs every answer against the corpus —
  * [[FilterInference.judge]] — and sends a failing one back with what it measured) · record (a filter that passed
  * becomes an `article-filter` approval the run does NOT wait for — approving it records the filter on the article —
  * or, with `agents.approvals.article-filter = auto`, is recorded at once) · close (the worklog line).
  *
  * A DRY run records the approval (or the write) it would make on its step and makes neither.
  */
final class InferFilterPipeline(site: ResearchSite, cfg: => AgentsConfig, research: => ResearchAgentsConfig)
    extends PipelineDef:
  val pipeline: Pipeline = Pipeline.InferFilter
  import InferFilterPipeline.*

  def run(ctx: RunCtx): Either[Halt, String] =
    val slug = ctx.run.target
    for
      loaded <- ctx.code[Loaded]("load")(load(slug))
      line <-
        if loaded.hasFilter then
          ctx.code[String]("close")(
            log(ctx, FilterInference.logLine(slug, "it records a filter already; nothing proposed", ctx.run.id))
          )
        else
          for
            inferred <- ctx.llm[FilterAnswer, Inferred](
              "propose",
              StepAgent.FilterInferrer,
              input(loaded),
              ResearchAnswers.filter
            )((a, last) => check(slug, loaded, a, last).map(Checked(_)))
            recorded <- ctx.code[Recorded]("record")(record(ctx, slug, inferred))
            closed <- ctx.code[String]("close")(log(ctx, FilterInference.logLine(slug, recorded.line, ctx.run.id)))
          yield closed
    yield line

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  private def load(slug: String): Either[String, Loaded] =
    site.articles.get(slug).left.map(_.message).map { v =>
      val a = v.article
      val stored = site.articleStore.load(a.slug)
      val cited = site.articles.citedSources(a.body)
      val refs =
        site.sourceStore
          .refs(cited.take(CitedShown))
          .map(r => s"[[${r.citekey}]] — ${r.title}${r.year.fold("")(y => s" ($y)")}")
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
        a.slug,
        a.title,
        a.kind,
        a.summary,
        a.tags,
        stored.flatMap(_.filter).isDefined,
        cited,
        refs,
        a.body.take(ExcerptChars),
        vocab("topics"),
        vocab("tags")
      )
    }

  private def input(l: Loaded): String =
    val lim = limits(research.inferFilter)
    val cited = if l.citedLines.isEmpty then "none"
    else
      l.citedLines.map(x => s"- $x").mkString("\n", "\n", "") +
        (if l.cited.size > l.citedLines.size then s"\n- … and ${l.cited.size - l.citedLines.size} more" else "")
    val article = s"slug: ${l.slug}\ntitle: ${l.title}\nkind: ${l.kind}\nsummary: ${l.summary.getOrElse("none")}\n" +
      s"tags: ${l.tags.mkString(", ")}"
    s"Infer the filter this article is compiled from.\n\n${Prompt.data("article", article)}\n\n" +
      s"${Prompt.data("article-text", l.excerpt)}\n\nThe sources it cites (${l.cited.size}): $cited\n\n" +
      s"The corpus's most used topics: ${l.topics.mkString(", ")}\nIts most used tags: ${l.tags0.mkString(", ")}\n\n" +
      f"Limits: the filter returns at least ${lim.minHits} and at most ${lim.maxHits} sources, and at least " +
      f"${lim.minCitedShare * 100}%.0f%% of the sources the article cites."

  /** An answer's filter, measured against the corpus and the article's citations. */
  private def check(slug: String, l: Loaded, a: FilterAnswer, last: Boolean): Either[List[String], Inferred] =
    FilterInference.filterOf(a) match
      case Left(ps) => if last then Right(Inferred(None, ps)) else Left(ps)
      case Right(f) =>
        val (total, in, rows) = site.articles.filterReach(f, l.cited, research.inferFilter.sample + l.cited.size)
        val sample = rows.filterNot(l.cited.contains).take(math.max(0, research.inferFilter.sample))
        FilterInference.judge(slug, f, a.rationale, total, l.cited, in, sample, limits(research.inferFilter), last)

  /** The proposal, through the write gate: an open approval (manual), or the filter recorded at once with an approval
    * already approved (auto). None passed: nothing is recorded.
    */
  private def record(ctx: RunCtx, slug: String, inf: Inferred): Either[String, Recorded] =
    inf.proposal match
      case None =>
        Right(Recorded(None, applied = false, s"no filter passed the checks: ${inf.problems.mkString("; ").take(600)}"))
      case Some(p) =>
        val json = p.asJson.noSpaces
        val what = s"${p.filter.line} → ${Plural.count(p.total, "source")}, ${p.citedIn} of ${p.cited} cited"
        cfg.approvals.filterMode match
          case com.histmersion.domain.automation.Approval.Mode.Auto =>
            ctx.write(s"record the coverage filter of [[$slug]]", p.asJson)(
              site.articles.patch(slug, Article.Patch(filter = Some(p.filter))).left.map(_.message).map { _ =>
                val a = ctx.approvalStore.openSubject(
                  Some(ctx.run.id),
                  ApprovalKind.ArticleFilter,
                  slug,
                  json,
                  ApprovalState.Approved,
                  Some(s"applied automatically (agents.approvals.article-filter): ${p.filter.line}")
                )
                Recorded(Some(a.id), applied = true, s"filter recorded: $what")
              }
            )(Right(Recorded(None, applied = false, s"filter not recorded (${AgentsMarkdown.DryRunNote}): $what")))
          case com.histmersion.domain.automation.Approval.Mode.Manual =>
            ctx.write(s"${ApprovalKind.ArticleFilter.wire} approval for [[$slug]]", p.asJson)(
              Right(ctx.approvalStore.openSubject(Some(ctx.run.id), ApprovalKind.ArticleFilter, slug, json))
                .map(a => Recorded(Some(a.id), applied = false, s"approval ${a.id} waits for a person: $what"))
            )(Right(Recorded(None, applied = false, s"no approval opened (${AgentsMarkdown.DryRunNote}): $what")))

  private def log(ctx: RunCtx, line: String): Either[String, String] = Pipelines.log(site.workLists, ctx, line)

object InferFilterPipeline:
  /** How many of the most used topics and tags the model is shown, how many cited sources by title, and how much of the
    * article's text.
    */
  val VocabularySize = 40
  val CitedShown = 40
  val ExcerptChars = 4000

  /** The checks a proposed filter is held to (`agents.infer-filter`), each within its range. */
  def limits(c: AgentInferFilterConfig): FilterInference.Limits = FilterInference.Limits(
    math.max(1, c.minHits),
    math.max(c.minHits, c.maxHits),
    math.min(1.0, math.max(0.0, c.minCitedShare))
  )

  /** The article as the step sees it (checkpointed). `tags0` = the corpus's tags. */
  final case class Loaded(
      slug: String,
      title: String,
      kind: String,
      summary: Option[String],
      tags: List[String],
      hasFilter: Boolean,
      cited: List[String],
      citedLines: List[String],
      excerpt: String,
      topics: List[String],
      tags0: List[String]
  ) derives KindlingsCodecAsObject

  /** What the record step did: the approval it opened (or recorded approved), whether the filter is on the article now,
    * and the line for the log.
    */
  final case class Recorded(approval: Option[Long], applied: Boolean, line: String) derives KindlingsCodecAsObject

  /** The proposal a decided `article-filter` approval carries, applied: the article's filter recorded — unless it
    * records one by now (a person set it meanwhile). Left = why it was not applied.
    */
  def applyProposal(articles: ArticlesControllers, store: ArticleService, p: FilterProposal): Either[String, String] =
    articles.get(p.slug).left.map(_.message).flatMap { v =>
      val has = store.load(v.article.slug).flatMap(_.filter)
      if has.isDefined then
        Left(s"[[${v.article.slug}]] records a filter already (${has.get.line}); the proposal was not applied")
      else
        articles
          .patch(v.article.slug, Article.Patch(filter = Some(p.filter)))
          .left
          .map(_.message)
          .map(_ => s"filter of [[${v.article.slug}]] recorded: ${p.filter.line}")
    }
