package com.histmersion.domain

import com.histmersion.api.{ArticleFilter, Plural}
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.ArticlePlans.FilterProposal

/** The `infer-filter` pipeline's typed answer and its pure checks (docs/research-process-managers.md §4 (a), phase 2):
  * an article that records no coverage filter (#958) gets one proposed by a model — free text, topics, tags and the
  * writer's further searches — and CODE decides whether it is fit to show a person: it must select something, return
  * between `minHits` and `maxHits` sources, and return at least `minCitedShare` of the sources the article already
  * cites (a filter that misses what the article is built on is not the article's filter). A failing answer goes back
  * with the measurements; on the last attempt nothing is proposed rather than a filter that failed.
  */
object FilterInference:

  final case class FilterAnswer(
      rationale: String,
      query: Option[String] = None,
      topics: List[String] = Nil,
      tags: List[String] = Nil,
      extraQueries: List[String] = Nil
  ) derives KindlingsCodecAsObject
  object FilterAnswer:
    given Schema[FilterAnswer] = KindlingsSchema
      .derived[FilterAnswer]
      .schema
      .modify(_.rationale)(_.description("Why these sources are the article's, in one or two sentences"))
      .modify(_.query)(
        _.description("Free text every source must match (search syntax: words AND-ed, \"a phrase\"); null for none")
      )
      .modify(_.topics)(_.description("Topics every source must have, from the corpus vocabulary (facets)"))
      .modify(_.tags)(_.description("Tags every source must have, from the corpus vocabulary (facets)"))
      .modify(_.extraQueries)(
        _.description("Further free-text searches worth reporting in the Method note; not part of the set")
      )

  /** The checks a filter must pass (config `agents.infer-filter.*`). */
  final case class Limits(minHits: Int, maxHits: Int, minCitedShare: Double)

  /** What the pipeline checkpoints: the proposal that passed, or None with why nothing did. */
  final case class Inferred(proposal: Option[FilterProposal], problems: List[String] = Nil)
      derives KindlingsCodecAsObject

  /** The answer as a filter: tidied ([[ArticleFilter.normalise]]); Left = what is wrong with its shape. */
  def filterOf(a: FilterAnswer): Either[List[String], ArticleFilter] =
    val raw = ArticleFilter(query = a.query, topics = a.topics, tags = a.tags, extraQueries = a.extraQueries)
    ArticleFilter.normalise(raw) match
      case Left(m)        => Left(List(m))
      case Right(None)    => Left(List("the filter selects nothing: give a query, topics or tags"))
      case Right(Some(f)) =>
        val rationale = Option(a.rationale).forall(_.trim.isEmpty)
        if rationale then Left(List("the rationale is empty — say why these sources are the article's")) else Right(f)

  /** Judge filter `f` of article `slug` by what it returns: `total` sources, of which the article's `cited` sources it
    * returns `citedIn`; `sample` = a few it returns the article does not cite. Left = the problems a retry should fix;
    * on the `last` attempt a failing filter becomes `Inferred(None, problems)` — nothing is proposed.
    */
  def judge(
      slug: String,
      f: ArticleFilter,
      rationale: String,
      total: Int,
      cited: List[String],
      citedIn: Set[String],
      sample: List[String],
      limits: Limits,
      last: Boolean
  ): Either[List[String], Inferred] =
    val missed = cited.filterNot(citedIn)
    val share = if cited.isEmpty then 1.0 else (cited.size - missed.size).toDouble / cited.size
    val problems = List(
      Option.when(total < limits.minHits)(
        s"${f.line} returns ${Plural.count(total, "source")}; it must return at least " +
          s"${limits.minHits} — widen it (fewer topics or tags, a broader query)"
      ),
      Option.when(total > limits.maxHits)(
        s"${f.line} returns $total sources; at most ${limits.maxHits} — narrow it " +
          "(add a topic or tag, a sharper query)"
      ),
      Option.when(share < limits.minCitedShare)(
        f"${f.line} returns ${cited.size - missed.size} of the ${cited.size} sources " +
          f"the article cites (${share * 100}%.0f%%); it must return at least ${limits.minCitedShare * 100}%.0f%% — it misses " +
          missed.take(12).mkString(", ")
      )
    ).flatten
    if problems.isEmpty then
      Right(
        Inferred(
          Some(FilterProposal(slug, f, rationale.trim, total, cited.size, cited.size - missed.size, missed, sample))
        )
      )
    else if last then Right(Inferred(None, problems))
    else Left(problems)

  /** The worklog line of a run. */
  def logLine(slug: String, outcome: String, runId: String): String =
    s"infer-filter [[$slug]]: $outcome (agent run ${runId.take(8)})"
