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
    note: ReviewFinding.Note = "",
    closedBy: Option[ReviewFinding.ClosedBy] = None,
    closedRound: Option[ReviewFinding.ClosedRound] = None,
    closedRunNumber: Option[ReviewFinding.ClosedRunNumber] = None,
    reopened: ReviewFinding.Reopened = 0,
    deferred: ReviewFinding.Deferred = false
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ReviewFinding:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reviewer = Reviewer.T
  object Reviewer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type ClosedBy = ClosedBy.T
  object ClosedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClosedRound = ClosedRound.T
  object ClosedRound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClosedRunNumber = ClosedRunNumber.T
  object ClosedRunNumber extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reopened = Reopened.T
  object Reopened extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Deferred = Deferred.T
  object Deferred extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
