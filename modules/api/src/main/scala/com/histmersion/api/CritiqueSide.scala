package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One source as a checklist pair shows it. `cited`: the article's body links it; `mentions`: in how many of its
  * paragraphs.
  */
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
