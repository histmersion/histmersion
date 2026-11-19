package com.histmersion
package api.research
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class ReviewRound(
    run: ReviewRound.Run,
    runNumber: ReviewRound.RunNumber,
    round: ReviewRound.Round,
    startedAt: Option[ReviewRound.StartedAt],
    findings: List[ReviewFinding]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ReviewRound:
  type Run = Run.T
  object Run extends Newtype[String, Pure]
  type RunNumber = RunNumber.T
  object RunNumber extends Newtype[Int, Pure]
  type Round = Round.T
  object Round extends Newtype[Int, Pure]
  type StartedAt = StartedAt.T
  object StartedAt extends Newtype[String, Pure]
