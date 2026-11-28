package com.histmersion
package domain.enrichsource

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.library
import com.histmersion.domain.EnrichSource.Topic
import com.histmersion.domain.EnrichSource.Tag
import com.histmersion.domain.EnrichSource.Region
import com.histmersion.domain.enrichsource.StatedPeriod
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.Newtype
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Content
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.Text.sentences
import com.histmersion.api.library.Source
import com.histmersion.domain.AgentVerify.*
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.EnrichSource.Last
import com.histmersion.domain.EnrichSource.normaliseTag
import com.histmersion.domain.enrichsource.Classified
import com.histmersion.domain.EnrichSource.CheckClassificationMaxAbsYear
import com.histmersion.domain.EnrichSource.Description

final case class Classification(
    summary: Classification.Summary,
    topics: List[Topic] = Nil,
    regions: List[Region] = Nil,
    tags: List[Tag] = Nil,
    period: Option[StatedPeriod],
    status: library.Source.Status.Wire,
    statusEvidence: Classification.StatusEvidence
) derives JsonCodec

object Classification:
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type StatusEvidence = StatusEvidence.T
  object StatusEvidence extends Newtype[String, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[Classification] = KindlingsSchema
    .derived[Classification]
    .schema
    .modify(_.summary)(_.description("3-6 sentences: what the source argues and on what evidence"))
    .modify(_.tags)(_.description("lowercase_snake_case; prefer tags the facets tool lists"))
    .modify(_.period)(
      _.description("The deep-time span the source is ABOUT (not its publication date); null if none")
    )
    .modify(_.status)(
      _.description("foundational | current | contested | superseded | debunked | retracted | unassessed")
    )
    .modify(_.statusEvidence)(_.description("Why that status, from the text; empty if unassessed"))

  extension (c: Classification)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def checkClassification(
        maxAbsYear: CheckClassificationMaxAbsYear,
        last: Last
    ): Either[List[Problem], (Classified, List[Description])] =
      val status = AgentVerify.status(c.status)
      val period = c.period.map(p => p.convert(maxAbsYear, None))
      val summary = Option(c.summary.value).map(_.trim).filter(_.nonEmpty)
      val evidence = Option(c.statusEvidence.value).map(_.trim).getOrElse("")
      val noEvidence = Option.when(status.exists(_ != Source.Status.Unassessed) && evidence.isEmpty)(
        Content.Answer(Content.Requirement.StatusEvidence)
      )
      val problems = status.left.toOption.toList ++
        summary.fold(List(Content.Answer(Content.Requirement.SummaryGiven)))(_ => Nil) ++
        period.flatMap(_.left.toOption).toList.flatMap(_.toVector) ++ noEvidence.toList
      val fatal = status.isLeft || summary.isEmpty
      if problems.isEmpty || (last.value && !fatal) then
        val clean = (l: List[String]) => l.map(_.trim).filter(_.nonEmpty).distinct
        Right(
          (
            Classified(
              Classified.Summary(summary.get),
              clean(c.topics.map(_.value)).map(Topic(_)),
              clean(c.regions.map(_.value)).map(Region(_)),
              clean(c.tags.map(_.value)).map(t => normaliseTag(Tag(t))),
              period.flatMap(_.toOption),
              c.period.flatMap(_.name),
              status.toOption.get,
              Classified.StatusEvidence(evidence)
            ),
            (period
              .flatMap(_.left.toOption)
              .map(m => s"period dropped — ${m.sentences.mkString("; ")}")
              .toList ++ noEvidence.map(_.sentence).toList)
              .map(Description(_))
          )
        )
      else Left(problems)
