package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class PassageHit(offset: Int, heading: Option[String], text: String, matches: List[MatchSpan])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object PassageHit:
  given Schema[PassageHit] = summon[KindlingsSchema[PassageHit]].schema
