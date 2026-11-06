package com.histmersion.api.views
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.ResolvedSource
import com.histmersion.api.research.ResolvedArticle
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class ResolvedId(
    id: ResolvedId.Id,
    source: Option[ResolvedSource] = None,
    article: Option[ResolvedArticle] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ResolvedId:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
