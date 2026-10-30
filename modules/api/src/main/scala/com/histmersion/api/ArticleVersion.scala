package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One version in the history of an article (list view — summary truncated, word count computed). */
final case class ArticleVersion(
    id: Long,
    slug: String,
    summary: Option[String],
    updatedAt: String,
    createdAt: String,
    wordCount: Int
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArticleVersion:
  given Schema[ArticleVersion] = summon[KindlingsSchema[ArticleVersion]].schema
