package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class CandidateMarked(state: com.histmersion.api.library.Source.Candidate.State, updated: CandidateMarked.Updated) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object CandidateMarked:
  type Updated = Updated.T
  object Updated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
