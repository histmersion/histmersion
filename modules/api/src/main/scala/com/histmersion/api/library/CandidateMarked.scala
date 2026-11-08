package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class CandidateMarked(state: Source.Candidate.State, updated: CandidateMarked.Updated) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object CandidateMarked:
  type Updated = Updated.T
  object Updated extends RefinedType[Int, Pure]
