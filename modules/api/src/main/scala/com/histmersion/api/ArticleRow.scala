package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class ArticleRow(
    slug: String,
    title: String,
    kind: String,
    body: String,
    summary: Option[String],
    tags: List[String],
    regions: List[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    updatedAt: Option[String] = None,
    uid: Option[java.util.UUID] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ArticleRow:
  given Schema[ArticleRow] = summon[KindlingsSchema[ArticleRow]].schema
