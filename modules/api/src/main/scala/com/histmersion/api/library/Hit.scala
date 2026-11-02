package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class Hit(source: SearchHit, matched: List[Finding.Match], why: List[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object Hit:
  given Schema[Hit] = summon[KindlingsSchema[Hit]].schema
