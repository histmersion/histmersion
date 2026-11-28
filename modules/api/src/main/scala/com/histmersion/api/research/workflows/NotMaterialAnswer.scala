package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.research.CritiqueRef
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.annotations.description

final case class NotMaterialAnswer(@description("The open pair, as src>dst") pair: NotMaterialAnswer.Pair, @description("One sentence: what the article cites the source for, and why the critique does not touch it (quote the article's words)") reason: NotMaterialAnswer.Rationale) derives JsonCodec, KindlingsSchema, Schema

object NotMaterialAnswer:
  export CritiqueRef.Wire as Pair
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
