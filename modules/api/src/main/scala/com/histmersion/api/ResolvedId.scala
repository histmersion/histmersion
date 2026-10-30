package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `GET /api/resolve/{id}` — what a bare identifier met in free text names: a stored source, an article, or nothing
  * (both empty). Always a 200: "names nothing" is an answer, not an error.
  */
final case class ResolvedId(
    id: String,
    source: Option[ResolvedSource] = None,
    article: Option[ResolvedArticle] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ResolvedId:
  given Schema[ResolvedId] = summon[KindlingsSchema[ResolvedId]].schema
