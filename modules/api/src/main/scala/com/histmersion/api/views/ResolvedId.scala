package com.histmersion.api.views
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.ResolvedSource
import com.histmersion.api.research.ResolvedArticle
import com.histmersion.api.given

final case class ResolvedId(
    id: String,
    source: Option[ResolvedSource] = None,
    article: Option[ResolvedArticle] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object ResolvedId:
  given Schema[ResolvedId] = summon[KindlingsSchema[ResolvedId]].schema
