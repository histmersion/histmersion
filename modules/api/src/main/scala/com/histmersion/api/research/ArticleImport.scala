package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ArticleImport(
    text: String,
    slug: Option[String] = None,
    kind: Option[String] = None,
    mtime: Option[Long] = None,
    force: Boolean = false,
    strict: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArticleImport:
  given Schema[ArticleImport] = summon[KindlingsSchema[ArticleImport]].schema
