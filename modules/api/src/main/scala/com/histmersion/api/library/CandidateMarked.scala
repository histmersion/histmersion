package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class CandidateMarked(state: Source.Candidate.State, updated: CandidateMarked.Updated)
    derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object CandidateMarked:
  type Updated = Updated.T
  object Updated extends Newtype[Int, Pure]
