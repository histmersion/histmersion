package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class ArticleDeleted(slug: String, deleted: Boolean) derives KindlingsCodecAsObject, KindlingsSchema
object ArticleDeleted:
  given Schema[ArticleDeleted] = summon[KindlingsSchema[ArticleDeleted]].schema
