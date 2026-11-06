package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
    note: ReviewFinding.Note = _root_.com.histmersion.api.research.ReviewFinding.Note(""),
    closedBy: Option[ReviewFinding.ClosedBy] = None,
    closedRound: Option[ReviewFinding.ClosedRound] = None,
    closedRunNumber: Option[ReviewFinding.ClosedRunNumber] = None,
    reopened: ReviewFinding.Reopened = _root_.com.histmersion.api.research.ReviewFinding.Reopened(0),
    deferred: ReviewFinding.Deferred = _root_.com.histmersion.api.research.ReviewFinding.Deferred(false)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ReviewFinding:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reviewer = Reviewer.T
  object Reviewer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Severity = Severity.T
  object Severity extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Kind = Kind.T
  object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Where = Where.T
  object Where extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Fix = Fix.T
  object Fix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Quote = Quote.T
  object Quote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Outcome = Outcome.T
  object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClosedBy = com.histmersion.api.automation.Activity.Actor
  val ClosedBy: _root_.com.histmersion.api.automation.Activity.Actor.type = _root_.com.histmersion.api.automation.Activity.Actor
  type ClosedRound = com.histmersion.api.research.ReviewRound.Round
  val ClosedRound: _root_.com.histmersion.api.research.ReviewRound.Round.type = _root_.com.histmersion.api.research.ReviewRound.Round
  type ClosedRunNumber = com.histmersion.api.research.ReviewRound.RunNumber
  val ClosedRunNumber: _root_.com.histmersion.api.research.ReviewRound.RunNumber.type = _root_.com.histmersion.api.research.ReviewRound.RunNumber
  type Reopened = Reopened.T
  object Reopened extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Deferred = Deferred.T
  object Deferred extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
