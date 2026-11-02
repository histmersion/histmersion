package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ArticleDeleted(slug: String, deleted: Boolean) derives KindlingsCodecAsObject, KindlingsSchema
object ArticleDeleted:
  given Schema[ArticleDeleted] = summon[KindlingsSchema[ArticleDeleted]].schema
