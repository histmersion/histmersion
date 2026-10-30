package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** DOIs and arXiv ids harvested from a Wikipedia article's wikitext — the trailhead. */
final case class WikirefsResult(
    article: String,
    url: String,
    doiCount: Int,
    dois: List[String],
    arxiv: List[String],
    known: List[String] = Nil // the subset of `dois` already stored, as citekeys
) derives KindlingsCodecAsObject,
      KindlingsSchema
object WikirefsResult:
  given Schema[WikirefsResult] = summon[KindlingsSchema[WikirefsResult]].schema
