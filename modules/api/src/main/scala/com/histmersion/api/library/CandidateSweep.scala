package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import io.circe.KeyEncoder
import io.circe.KeyDecoder

final case class CandidateSweep(
    goal: CandidateSweep.Goal,
    watches: List[CandidateWatch],
    found: CandidateSweep.Found,
    added: CandidateSweep.Added,
    again: CandidateSweep.Again,
    skipped: Map[CandidateSweep.SkippedKey, CandidateSweep.SkippedValue],
    unseen: CandidateSweep.Unseen,
    dryRun: CandidateSweep.DryRun = CandidateSweep.DryRun(false),
    notes: List[CandidateSweep.Notes] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object CandidateSweep:

  given Schema[Map[SkippedKey, SkippedValue]] = Schema.schemaForMap[SkippedKey, SkippedValue](_.value).copy(name = None, isOptional = true)

  export com.histmersion.api.research.ReviewGoal.{Slug as Goal}
  type Found = Found.T
  object Found extends RefinedType[Int, Pure]
  type Added = Added.T
  object Added extends RefinedType[Int, Pure]
  type Again = Again.T
  object Again extends RefinedType[Int, Pure]
  type SkippedKey = SkippedKey.T
  object SkippedKey extends RefinedType[String, Pure]:
    given scala.math.Ordering[T] = scala.math.Ordering.by[T, String](_.value)
    given KeyEncoder[T] = KeyEncoder.encodeKeyString.contramap(_.value)
    given KeyDecoder[T] = KeyDecoder.decodeKeyString.map(SkippedKey(_))
  type SkippedValue = SkippedValue.T
  object SkippedValue extends RefinedType[Int, Pure]
  type Unseen = Unseen.T
  object Unseen extends RefinedType[Int, Pure]
  type DryRun = DryRun.T
  object DryRun extends RefinedType[Boolean, Pure]
  type Notes = Notes.T
  object Notes extends RefinedType[String, Pure]

  final case class Request(goal: Request.Goal, dryRun: Request.DryRun = Request.DryRun(false)) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object Request:
    export com.histmersion.api.research.ReviewGoal.{Slug as Goal}
    type DryRun = DryRun.T
    object DryRun extends RefinedType[Boolean, Pure]
