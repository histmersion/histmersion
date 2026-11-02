package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CandidateMarked(state: String, updated: Int) derives KindlingsCodecAsObject, KindlingsSchema
object CandidateMarked:
  given Schema[CandidateMarked] = summon[KindlingsSchema[CandidateMarked]].schema
