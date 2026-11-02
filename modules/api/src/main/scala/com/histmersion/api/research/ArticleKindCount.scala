package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ArticleKindCount(kind: String, count: Int) derives KindlingsCodecAsObject, KindlingsSchema
object ArticleKindCount:
  given Schema[ArticleKindCount] = summon[KindlingsSchema[ArticleKindCount]].schema
