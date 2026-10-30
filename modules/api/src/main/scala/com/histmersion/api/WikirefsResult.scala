package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class WikirefsResult(
    article: String,
    url: String,
    doiCount: Int,
    dois: List[String],
    arxiv: List[String],
    known: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object WikirefsResult:
  given Schema[WikirefsResult] = summon[KindlingsSchema[WikirefsResult]].schema
