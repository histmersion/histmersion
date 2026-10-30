package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class ResolvedArticle(slug: String, title: String) derives KindlingsCodecAsObject, KindlingsSchema
object ResolvedArticle:
  given Schema[ResolvedArticle] = summon[KindlingsSchema[ResolvedArticle]].schema
