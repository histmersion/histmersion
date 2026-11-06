package com.histmersion.domain

import com.histmersion.api.Plural
import com.histmersion.api.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import com.histmersion.api.research.Article.Plans.FilterProposal
import com.histmersion.api.research.Article

/** The `infer-filter` pipeline's typed answer and its pure checks (docs/research-process-managers.md §4 (a), phase 2):
  * an article that records no coverage filter (#958) gets one proposed by a model — free text, topics, tags and the
  * writer's further searches — and CODE decides whether it is fit to show a person: it must select something, return
  * between `minHits` and `maxHits` sources, and return at least `minCitedShare` of the sources the article already
  * cites (a filter that misses what the article is built on is not the article's filter). A failing answer goes back
  * with the measurements; on the last attempt nothing is proposed rather than a filter that failed.
  */
object FilterInference:
  type Last = Last.T
  object Last extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LogLine = LogLine.T
  object LogLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Outcome = Outcome.T
  object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class FilterAnswer(
      rationale: FilterAnswer.Rationale,
      query: Option[Article.Filter.Query] = None,
      topics: List[Article.Filter.Topics] = Nil,
      tags: List[Article.Filter.Tags] = Nil,
      extraQueries: List[Article.Filter.ExtraQueries] = Nil
  ) derives KindlingsCodecAsObject
  object FilterAnswer:
    type Rationale = Rationale.T
    object Rationale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  final case class Limits(minHits: Limits.MinHits, maxHits: Limits.MaxHits, minCitedShare: Limits.MinCitedShare)

  object Limits:
    type MinHits = MinHits.T
    object MinHits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxHits = MaxHits.T
    object MaxHits extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinCitedShare = MinCitedShare.T
    object MinCitedShare extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What the pipeline checkpoints: the proposal that passed, or None with why nothing did. */
  final case class Inferred(proposal: Option[FilterProposal], problems: List[Problem] = Nil)
      derives KindlingsCodecAsObject

  /** The answer as a filter: tidied ([[ArticleFilter.normalise]]); Left = what is wrong with its shape. */
  def filterOf(a: FilterAnswer): Either[List[Problem], Article.Filter] =
    val raw = Article.Filter(query = a.query, topics = a.topics, tags = a.tags, extraQueries = a.extraQueries)
    Article.Filter.normalise(raw) match
      case Left(m)        => Left(List(Problem(m.value)))
      case Right(None)    => Left(List(Problem("the filter selects nothing: give a query, topics or tags")))
      case Right(Some(f)) =>
        val rationale = Option(a.rationale).forall(_.value.trim.isEmpty)
        if rationale then Left(List(Problem("the rationale is empty — say why these sources are the article's"))) else Right(f)

  /** Judge filter `f` of article `slug` by what it returns: `total` sources, of which the article's `cited` sources it
    * returns `citedIn`; `sample` = a few it returns the article does not cite. Left = the problems a retry should fix;
    * on the `last` attempt a failing filter becomes `Inferred(None, problems)` — nothing is proposed.
    */
  def judge(
      slug: com.histmersion.domain.research.Slug,
      f: Article.Filter,
      rationale: com.histmersion.domain.FilterInference.FilterAnswer.Rationale,
      total: FilterProposal.Total,
      cited: List[com.histmersion.domain.library.Citekey],
      citedIn: Set[com.histmersion.domain.library.Citekey],
      sample: List[com.histmersion.domain.library.Citekey],
      limits: Limits,
      last: Last
  ): Either[List[Problem], Inferred] =
    val missed = cited.filterNot(citedIn)
    val share = if cited.isEmpty then 1.0 else (cited.size - missed.size).toDouble / cited.size
    val problems = List(
      Option.when(total.value < limits.minHits.value)(
        Problem(
          s"${f.line} returns ${Plural.count(Plural.CountN(total.value.toLong), Plural.CountSingular("source"))}; it must return at least " +
            s"${limits.minHits} — widen it (fewer topics or tags, a broader query)"
        )
      ),
      Option.when(total.value > limits.maxHits.value)(
        Problem(
          s"${f.line} returns $total sources; at most ${limits.maxHits} — narrow it " +
            "(add a topic or tag, a sharper query)"
        )
      ),
      Option.when(share < limits.minCitedShare.value)(
        Problem(
          f"${f.line} returns ${cited.size - missed.size} of the ${cited.size} sources " +
            f"the article cites (${share * 100}%.0f%%); it must return at least ${limits.minCitedShare.value * 100}%.0f%% — it misses " +
            missed.take(12).mkString(", ")
        )
      )
    ).flatten
    if problems.isEmpty then
      Right(
        Inferred(
          Some(
            FilterProposal(
              Article.Slug(slug.value),
              f,
              FilterProposal.Rationale(rationale.value.trim),
              total,
              FilterProposal.Cited(cited.size),
              FilterProposal.CitedIn(cited.size - missed.size),
              missed.map(c => FilterProposal.Missed(c.value)),
              sample.map(c => FilterProposal.Sample(c.value))
            )
          )
        )
      )
    else if last.value then Right(Inferred(None, problems))
    else Left(problems)

  /** The worklog line of a run. */
  def logLine(slug: com.histmersion.domain.research.Slug, outcome: com.histmersion.domain.FilterInference.Outcome, runId: com.histmersion.domain.automation.Run.Id): com.histmersion.domain.FilterInference.LogLine =
    LogLine(s"infer-filter [[$slug]]: $outcome (agent run ${runId.value.take(8)})")
