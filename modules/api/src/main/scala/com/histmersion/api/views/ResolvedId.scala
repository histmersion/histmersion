package com.histmersion
package api.views
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.ResolvedSource
import com.histmersion.api.research.ResolvedArticle
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class ResolvedId(
    id: ResolvedId.Id,
    source: Option[ResolvedSource] = None,
    article: Option[ResolvedArticle] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ResolvedId:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
