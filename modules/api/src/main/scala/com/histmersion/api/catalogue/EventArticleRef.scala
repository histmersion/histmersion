package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class EventArticleRef(slug: com.histmersion.api.research.Article.Slug, title: com.histmersion.api.research.Article.Title, citekey: com.histmersion.api.library.Citekey)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
