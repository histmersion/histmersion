package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CandidateMark(ids: List[Long], state: String) derives KindlingsCodecAsObject, KindlingsSchema
object CandidateMark:
  given Schema[CandidateMark] = summon[KindlingsSchema[CandidateMark]].schema
