package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class CandidateCount(state: String, count: Int) derives KindlingsCodecAsObject, KindlingsSchema
object CandidateCount:
  given Schema[CandidateCount] = summon[KindlingsSchema[CandidateCount]].schema
