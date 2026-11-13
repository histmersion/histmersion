package com.histmersion
package api.research
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class ResolvedArticle(slug: Article.Slug, title: Article.Title)
    derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema
