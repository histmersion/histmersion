package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.workflows.{PipelineDef, Pipelines}

import com.histmersion.api.Plural
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalMode, ApprovalState, Pipeline}
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.api.library.Facet
import com.histmersion.api.research.Article.Plans.FilterProposal
import com.histmersion.domain.FilterInference.{FilterAnswer, Inferred}
import com.histmersion.domain.research.articles.{ArticlesControllers, ArticleService}
import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.syntax.*
import com.histmersion.api.automation.AgentsMarkdown
import com.histmersion.api.library.Filter
import com.histmersion.api.research.Article
import com.histmersion.domain.Title
import com.histmersion.util.jobs.Job
import com.histmersion.domain.automation.Approval
import io.github.iltotore.iron.constraint.any.Pure

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

  def run(ctx: RunCtx): Either[Halt, PipelineDef.Summary] =
    Slug.parse(ctx.run.target.value).left.map(p => Halt.Fail(Halt.Fail.Message(p.value))).flatMap(runFor(ctx, _))

  private def runFor(ctx: RunCtx, slug: Slug): Either[Halt, PipelineDef.Summary] =
    val runId = ctx.runId
    for
      loaded <- ctx.code[Loaded](AgentRepo.StepRecord.Name("load"))(load(slug))
      line <-
        if loaded.hasFilter then
          ctx.code[String](AgentRepo.StepRecord.Name("close"))(
            log(
              ctx,
              FilterInference
                .logLine(slug, FilterInference.Outcome("it records a filter already; nothing proposed"), runId)
            )
          )
        else
          for
            inferred <- ctx.llm[FilterAnswer, Inferred](
              AgentRepo.StepRecord.Name("propose"),
              StepAgent.FilterInferrer,
              Prompt.UserText(input(loaded)),
              ResearchAnswers.filter
            )((a, last) =>
              check(slug, loaded, a, FilterInference.Last(last.value)).left
                .map(_.map(p => Problem(p.value)))
                .map(Checked(_))
            )
            recorded <- ctx.code[Recorded](AgentRepo.StepRecord.Name("record"))(record(ctx, slug, inferred))
            closed <- ctx.code[String](AgentRepo.StepRecord.Name("close"))(
              log(ctx, FilterInference.logLine(slug, FilterInference.Outcome(recorded.line.value), runId))
            )
          yield closed
    yield PipelineDef.Summary(line)

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  private def load(slug: Slug): Either[String, Loaded] =
    site.articles.get(slug).left.map(_.message.value).map { v =>
      val a = v.article
      val canonical = Slug.assume(a.slug.value)
      val stored = site.articleStore.load(canonical)
      val cited = site.articles.citedSources(a.body)
      val refs =
        site.sourceStore
          .refs(cited.take(CitedShown))
          .map(r => s"[[${r.citekey}]] — ${r.title}${r.year.fold("")(y => s" ($y)")}")
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
        canonical,
        Title(a.title.value),
        com.histmersion.domain.research.Article.Kind(a.kind.value),
        a.summary.map(s => Loaded.Summary(s.value)),
        a.tags.map(t => Loaded.Tags(t.value)),
        stored.flatMap(_.filter).isDefined,
        cited,
        refs.map(Loaded.CitedLines(_)),
        Loaded.Excerpt(a.body.value.take(ExcerptChars)),
        vocab("topics").map(Loaded.Topics(_)),
        vocab("tags").map(Loaded.Tags(_))
      )
    }

  private def input(l: Loaded): String =
    val lim = limits(research.inferFilter)
    val cited = if l.citedLines.isEmpty then "none"
    else
      l.citedLines.map(x => s"- $x").mkString("\n", "\n", "") +
        (if l.cited.size > l.citedLines.size then s"\n- … and ${l.cited.size - l.citedLines.size} more" else "")
    val article =
      s"slug: ${l.slug}\ntitle: ${l.title}\nkind: ${l.kind}\nsummary: ${l.summary.fold("none")(_.value)}\n" +
        s"tags: ${l.tags.mkString(", ")}"
    s"Infer the filter this article is compiled from.\n\n${Prompt.data("article", article)}\n\n" +
      s"${Prompt.data("article-text", l.excerpt.value)}\n\nThe sources it cites (${l.cited.size}): $cited\n\n" +
      s"The corpus's most used topics: ${l.topics.mkString(", ")}\nIts most used tags: ${l.tags0.mkString(", ")}\n\n" +
      f"Limits: the filter returns at least ${lim.minHits} and at most ${lim.maxHits} sources, and at least " +
      f"${lim.minCitedShare.value * 100}%.0f%% of the sources the article cites."

  /** An answer's filter, measured against the corpus and the article's citations. */
  private def check(
      slug: Slug,
      l: Loaded,
      a: FilterAnswer,
      last: FilterInference.Last
  ): Either[List[Problem], Inferred] =
    FilterInference.filterOf(a) match
      case Left(ps) => if last.value then Right(Inferred(None, ps)) else Left(ps)
      case Right(f) =>
        val cap = ArticlesControllers.FilterReachCap(research.inferFilter.sample.value + l.cited.size)
        val (total, in, rows) = site.articles.filterReach(f, l.cited, cap)
        val sample = rows
          .map(r => Citekey.assume(r.value))
          .filterNot(l.cited.contains)
          .take(math.max(0, research.inferFilter.sample.value))
        FilterInference.judge(
          slug,
          f,
          a.rationale,
          FilterProposal.Total(total.value),
          l.cited,
          in,
          sample,
          limits(research.inferFilter),
          last
        )

  /** The proposal, through the write gate: an open approval (manual), or the filter recorded at once with an approval
    * already approved (auto). None passed: nothing is recorded.
    */
  private def record(ctx: RunCtx, slug: Slug, inf: Inferred): Either[String, Recorded] =
    inf.proposal match
      case None =>
        Right(
          Recorded(
            None,
            applied = false,
            Recorded.Line(s"no filter passed the checks: ${inf.problems.mkString("; ").take(600)}")
          )
        )
      case Some(p) =>
        val json = p.asJson.noSpaces
        val jobId = Some(Job.Id.assume(ctx.run.id.value))
        val subject = ApprovalQuery.Subject(slug.value)
        val what =
          s"${p.filter.line} → ${Plural.count(Plural.CountN(p.total.value.toLong), Plural.CountSingular("source"))}, ${p.citedIn} of ${p.cited} cited"
        cfg.approvals.filterMode match
          case Approval.Mode.Auto =>
            ctx.write(s"record the coverage filter of [[$slug]]", p.asJson)(
              site.articles.patch(slug, Article.Patch(filter = Some(p.filter))).left.map(_.message.value).map { _ =>
                val a = ctx.approvalStore.openSubject(
                  jobId,
                  ApprovalKind.ArticleFilter,
                  subject,
                  ApprovalService.Payload(json),
                  ApprovalState.Approved,
                  Some(
                    ApprovalService.OpenSubjectResult(
                      s"applied automatically (agents.approvals.article-filter): ${p.filter.line}"
                    )
                  )
                )
                Recorded(Some(a.id), applied = true, Recorded.Line(s"filter recorded: $what"))
              }
            )(
              Right(
                Recorded(
                  None,
                  applied = false,
                  Recorded.Line(s"filter not recorded (${AgentsMarkdown.DryRunNote}): $what")
                )
              )
            )
          case Approval.Mode.Manual =>
            ctx.write(s"${ApprovalKind.ArticleFilter.wire} approval for [[$slug]]", p.asJson)(
              Right(
                ctx.approvalStore.openSubject(jobId, ApprovalKind.ArticleFilter, subject, ApprovalService.Payload(json))
              )
                .map(a =>
                  Recorded(
                    Some(a.id),
                    applied = false,
                    Recorded.Line(s"approval ${a.id} waits for a person: $what")
                  )
                )
            )(
              Right(
                Recorded(
                  None,
                  applied = false,
                  Recorded.Line(s"no approval opened (${AgentsMarkdown.DryRunNote}): $what")
                )
              )
            )

  private def log(ctx: RunCtx, line: FilterInference.LogLine): Either[String, String] =
    Pipelines.log(site.workLists, ctx, Pipelines.LogLine(line.value)).left.map(_.value).map(_.value)

object InferFilterPipeline:
  /** How many of the most used topics and tags the model is shown, how many cited sources by title, and how much of the
    * article's text.
    */
  val VocabularySize = 40
  val CitedShown = 40
  val ExcerptChars = 4000

  /** The checks a proposed filter is held to (`agents.infer-filter`), each within its range. */
  def limits(c: AgentInferFilterConfig): FilterInference.Limits = FilterInference.Limits(
    FilterInference.Limits.MinHits(math.max(1, c.minHits.value)),
    FilterInference.Limits.MaxHits(math.max(c.minHits.value, c.maxHits.value)),
    FilterInference.Limits.MinCitedShare(math.min(1.0, math.max(0.0, c.minCitedShare.value)))
  )

  /** The article as the step sees it (checkpointed). `tags0` = the corpus's tags. */
  final case class Loaded(
      slug: Slug,
      title: Title,
      kind: com.histmersion.domain.research.Article.Kind,
      summary: Option[Loaded.Summary],
      tags: List[Loaded.Tags],
      hasFilter: Boolean,
      cited: List[Citekey],
      citedLines: List[Loaded.CitedLines],
      excerpt: Loaded.Excerpt,
      topics: List[Loaded.Topics],
      tags0: List[Loaded.Tags]
  ) derives KindlingsCodecAsObject

  object Loaded:
    type Summary = Summary.T
    object Summary extends Newtype[String, Pure]
    type Tags = Tags.T
    object Tags extends Newtype[String, Pure]
    type CitedLines = CitedLines.T
    object CitedLines extends Newtype[String, Pure]
    type Excerpt = Excerpt.T
    object Excerpt extends Newtype[String, Pure]
    type Topics = Topics.T
    object Topics extends Newtype[String, Pure]

  /** What the record step did: the approval it opened (or recorded approved), whether the filter is on the article now,
    * and the line for the log.
    */
  final case class Recorded(approval: Option[Agents.ApprovalInfo.Id], applied: Boolean, line: Recorded.Line)
      derives KindlingsCodecAsObject

  object Recorded:
    type Line = Line.T
    object Line extends Newtype[String, Pure]

  /** The proposal a decided `article-filter` approval carries, applied: the article's filter recorded — unless it
    * records one by now (a person set it meanwhile). Left = why it was not applied.
    */
  def applyProposal(articles: ArticlesControllers, store: ArticleService, p: FilterProposal): Either[String, String] =
    Slug.parse(p.slug.value).left.map(_.value).flatMap { slug =>
      articles.get(slug).left.map(_.message.value).flatMap { v =>
        val canonical = Slug.assume(v.article.slug.value)
        val has = store.load(canonical).flatMap(_.filter)
        if has.isDefined then
          Left(s"[[${v.article.slug}]] records a filter already (${has.get.line}); the proposal was not applied")
        else
          articles
            .patch(canonical, Article.Patch(filter = Some(p.filter)))
            .left
            .map(_.message.value)
            .map(_ => s"filter of [[${v.article.slug}]] recorded: ${p.filter.line}")
      }
    }
