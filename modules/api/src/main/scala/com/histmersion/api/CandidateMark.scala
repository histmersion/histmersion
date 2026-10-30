package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Set candidates' state (`seen`, `queued`, `dismissed`, or `new` again). */
final case class CandidateMark(ids: List[Long], state: String) derives KindlingsCodecAsObject, KindlingsSchema
object CandidateMark:
  given Schema[CandidateMark] = summon[KindlingsSchema[CandidateMark]].schema
