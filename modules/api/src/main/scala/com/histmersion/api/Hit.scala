package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class Hit(source: SearchHit, matched: List[FindingMatch], why: List[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object Hit:
  given Schema[Hit] = summon[KindlingsSchema[Hit]].schema
