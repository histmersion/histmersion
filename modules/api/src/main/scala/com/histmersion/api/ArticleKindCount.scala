package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** How many listed articles are of one kind. */
final case class ArticleKindCount(kind: String, count: Int) derives KindlingsCodecAsObject, KindlingsSchema
object ArticleKindCount:
  given Schema[ArticleKindCount] = summon[KindlingsSchema[ArticleKindCount]].schema
