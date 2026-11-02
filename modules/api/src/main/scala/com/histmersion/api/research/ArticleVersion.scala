package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
