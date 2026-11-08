package com.histmersion
package api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class ReviewFinding(
    id: ReviewFinding.Id,
    reviewer: ReviewFinding.Reviewer,
    severity: ReviewFinding.Severity,
    kind: ReviewFinding.Kind,
    where: ReviewFinding.Where,
    problem: ReviewFinding.Problem,
    fix: ReviewFinding.Fix,
    quote: ReviewFinding.Quote,
    outcome: ReviewFinding.Outcome,
    note: ReviewFinding.Note = ReviewFinding.Note(""),
    closedBy: Option[ReviewFinding.ClosedBy] = None,
    closedRound: Option[ReviewFinding.ClosedRound] = None,
    closedRunNumber: Option[ReviewFinding.ClosedRunNumber] = None,
    reopened: ReviewFinding.Reopened = ReviewFinding.Reopened(0),
    deferred: ReviewFinding.Deferred = ReviewFinding.Deferred(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ReviewFinding:
  type Id = Id.T
  object Id extends RefinedType[String, Pure]
  type Reviewer = Reviewer.T
  object Reviewer extends RefinedType[String, Pure]:
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]
  type Severity = Severity.T
  object Severity extends RefinedType[String, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]
  type Where = Where.T
  object Where extends RefinedType[String, Pure]
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
  type Fix = Fix.T
  object Fix extends RefinedType[String, Pure]
  type Quote = Quote.T
  object Quote extends RefinedType[String, Pure]
  type Outcome = Outcome.T
  object Outcome extends RefinedType[String, Pure]
  type Note = Note.T
  object Note extends RefinedType[String, Pure]
  export com.histmersion.api.automation.Activity.{Actor as ClosedBy}
  export ReviewRound.{Round as ClosedRound, RunNumber as ClosedRunNumber}
  type Reopened = Reopened.T
  object Reopened extends RefinedType[Int, Pure]
  type Deferred = Deferred.T
  object Deferred extends RefinedType[Boolean, Pure]
