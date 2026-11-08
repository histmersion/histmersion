package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.research.Article
import com.histmersion.api.library.Citekey

final case class EventArticleRef(slug: Article.Slug, title: Article.Title, citekey: Citekey)
    derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema
