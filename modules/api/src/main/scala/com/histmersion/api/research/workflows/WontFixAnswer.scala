package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.annotations.description

final case class WontFixAnswer(@description("The id of the finding you decline") id: WontFixAnswer.Id, @description("Why, in one sentence") reason: WontFixAnswer.Rationale) derives JsonCodec, KindlingsSchema, Schema

object WontFixAnswer:
  export ReviewFinding.Id
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
