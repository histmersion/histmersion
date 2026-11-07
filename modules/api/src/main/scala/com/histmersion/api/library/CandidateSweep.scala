package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class CandidateSweep(
    goal: CandidateSweep.Goal,
    watches: List[CandidateWatch],
    found: CandidateSweep.Found,
    added: CandidateSweep.Added,
    again: CandidateSweep.Again,
    skipped: Map[CandidateSweep.SkippedKey, CandidateSweep.SkippedValue],
    unseen: CandidateSweep.Unseen,
    dryRun: CandidateSweep.DryRun = _root_.com.histmersion.api.library.CandidateSweep.DryRun(false),
    notes: List[CandidateSweep.Notes] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object CandidateSweep:

  given Schema[Map[SkippedKey, SkippedValue]] = Schema.schemaForMap[SkippedKey, SkippedValue](_.value).copy(name = None, isOptional = true)

  type Goal = com.histmersion.api.research.ReviewGoal.Slug
  val Goal: _root_.com.histmersion.api.research.ReviewGoal.Slug.type = _root_.com.histmersion.api.research.ReviewGoal.Slug
  type Found = Found.T
  object Found extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Added = Added.T
  object Added extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Again = Again.T
  object Again extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SkippedKey = SkippedKey.T
  object SkippedKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
    given _root_.io.circe.KeyEncoder[T] = _root_.io.circe.KeyEncoder.encodeKeyString.contramap(_.value)
    given _root_.io.circe.KeyDecoder[T] = _root_.io.circe.KeyDecoder.decodeKeyString.map(SkippedKey(_))
  type SkippedValue = SkippedValue.T
  object SkippedValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unseen = Unseen.T
  object Unseen extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Notes = Notes.T
  object Notes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Request(goal: Request.Goal, dryRun: Request.DryRun = _root_.com.histmersion.api.library.CandidateSweep.Request.DryRun(false)) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Request:
    type Goal = com.histmersion.api.research.ReviewGoal.Slug
    val Goal: _root_.com.histmersion.api.research.ReviewGoal.Slug.type = _root_.com.histmersion.api.research.ReviewGoal.Slug
    type DryRun = DryRun.T
    object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
