package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ResolvedArticle(slug: String, title: String) derives KindlingsCodecAsObject, KindlingsSchema
object ResolvedArticle:
  given Schema[ResolvedArticle] = summon[KindlingsSchema[ResolvedArticle]].schema
