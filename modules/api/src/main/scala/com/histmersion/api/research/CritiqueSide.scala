package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CritiqueSide(
    citekey: String,
    title: Option[String] = None,
    year: Option[Int] = None,
    cited: Boolean = false,
    mentions: Int = 0
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
