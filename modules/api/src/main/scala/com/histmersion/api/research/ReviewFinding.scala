package com.histmersion
package api.research
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.TwoStates

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
    deferred: ReviewFinding.Deferred = ReviewFinding.Deferred.Open
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ReviewFinding:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Reviewer = Reviewer.T
  object Reviewer extends Newtype[String, Pure]:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
  type Severity = Severity.T
  object Severity extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Where = Where.T
  object Where extends Newtype[String, Pure]
  type Problem = Problem.T
  @tolerated(LintRule.G5Problem, "pending G60 (one typed Problem ADT)")
  object Problem extends Newtype[String, Pure]
  type Fix = Fix.T
  object Fix extends Newtype[String, Pure]
  type Quote = Quote.T
  object Quote extends Newtype[String, Pure]
  type Outcome = Outcome.T
  object Outcome extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
  export api.automation.Activity.Actor as ClosedBy
  export ReviewRound.{Round as ClosedRound, RunNumber as ClosedRunNumber}
  type Reopened = Reopened.T
  object Reopened extends Newtype[Int, Pure]
  enum Deferred derives CanEqual:
    case Deferred, Open
  object Deferred extends TwoStates[Deferred]:
    extension (deferred: Deferred) def deferred: Boolean = deferred == Deferred
