package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class ResolvedArticle(slug: com.histmersion.api.research.Article.Slug, title: com.histmersion.api.research.Article.Title) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
