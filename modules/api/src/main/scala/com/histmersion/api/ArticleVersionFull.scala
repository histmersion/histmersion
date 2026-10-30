package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One historical version of an article's body (detail view — full body included). */
final case class ArticleVersionFull(
    id: Long,
    slug: String,
    body: String,
    summary: Option[String],
    updatedAt: String,
    createdAt: String
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArticleVersionFull:
  given Schema[ArticleVersionFull] = summon[KindlingsSchema[ArticleVersionFull]].schema
