package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class CritiqueSide(
    citekey: String,
    title: Option[String] = None,
    year: Option[Int] = None,
    cited: Boolean = false,
    mentions: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema
object CritiqueSide:
  given Schema[CritiqueSide] = summon[KindlingsSchema[CritiqueSide]].schema
