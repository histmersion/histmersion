package com.histmersion
package domain

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.research.Article
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.util.problem.Problem
import com.histmersion.api.research.Article.Plans.FilterProposal
import com.histmersion.util.problem.Problem.Content
import com.histmersion.util.problem.Problem.Count
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.domain.research.Slug
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.automation.Run

final case class FilterAnswer(
    rationale: FilterAnswer.Rationale,
    query: Option[Article.Filter.Query] = None,
    topics: List[Article.Filter.Topics] = Nil,
    tags: List[Article.Filter.Tags] = Nil,
    extraQueries: List[Article.Filter.ExtraQueries] = Nil
) derives JsonCodec

object FilterAnswer:
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
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

  type Last = Last.T

  object Last extends Newtype[Boolean, Pure]

  type LogLine = LogLine.T

  object LogLine extends Newtype[String, Pure]

  type Outcome = Outcome.T

  object Outcome extends Newtype[String, Pure]

  final case class Limits(minHits: Limits.MinHits, maxHits: Limits.MaxHits, minCitedShare: Limits.MinCitedShare)

  object Limits:
    type MinHits = MinHits.T
    object MinHits extends Newtype[Int, Pure]
    type MaxHits = MaxHits.T
    object MaxHits extends Newtype[Int, Pure]
    type MinCitedShare = MinCitedShare.T
    object MinCitedShare extends Newtype[Double, Pure]

  final case class Inferred(proposal: Option[FilterProposal], problems: List[Problem] = Nil) derives JsonCodec

  extension (a: FilterAnswer)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def filterOf: Either[List[Problem], Article.Filter] =
      val raw = Article.Filter(query = a.query, topics = a.topics, tags = a.tags, extraQueries = a.extraQueries)
      Article.Filter.normalise(raw) match
        case Left(m)     => Left(List(m))
        case Right(None) =>
          Left(List(Content.Answer(Content.Requirement.FilterSelects)))
        case Right(Some(f)) =>
          val rationale = Option(a.rationale).forall(_.value.trim.isEmpty)
          if rationale then Left(List(Content.Answer(Content.Requirement.FilterRationale)))
          else Right(f)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def judge(
      slug: Slug,
      f: Article.Filter,
      rationale: FilterAnswer.Rationale,
      total: FilterProposal.Total,
      cited: List[Citekey],
      citedIn: Set[Citekey],
      sample: List[Citekey],
      limits: Limits,
      last: Last
  ): Either[List[Problem], Inferred] =
    val missed = cited.filterNot(citedIn)
    val share = if cited.isEmpty then 1.0 else (cited.size - missed.size).toDouble / cited.size
    val n = total.value
    val (least, most, leastShare) = (limits.minHits.value, limits.maxHits.value, limits.minCitedShare.value)
    val line = Raw.of(f.shownLine)
    def refused(rule: Content.Requirement): Problem = Content.Answer(rule)
    val problems = List(
      Option.when(n < least)(
        refused(Content.Requirement.FilterTooNarrow(line, Count(n.toLong), Count(least.toLong)))
      ),
      Option.when(n > most)(
        refused(Content.Requirement.FilterTooBroad(line, Count(n.toLong), Count(most.toLong)))
      ),
      Option.when(share < leastShare)(
        refused(
          Content.Requirement.FilterMisses(
            line,
            Count((cited.size - missed.size).toLong),
            Count(cited.size.toLong),
            Count(math.round(share * 100)),
            Count(math.round(leastShare * 100)),
            missed.take(12).toVector.map(Raw.of(_))
          )
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def logLine(slug: Slug, outcome: Outcome, runId: Run.Id): LogLine =
    LogLine(s"infer-filter [[$slug]]: $outcome (agent run ${runId.value.take(8)})")
