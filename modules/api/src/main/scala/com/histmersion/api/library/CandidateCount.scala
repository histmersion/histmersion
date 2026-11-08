package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class CandidateCount(state: Source.Candidate.State, count: CandidateCount.Count)
    derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object CandidateCount:
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
