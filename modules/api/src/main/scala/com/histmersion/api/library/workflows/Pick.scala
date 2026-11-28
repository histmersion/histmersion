package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.annotations.description

final case class Pick(@description("The DOI of a candidate, exactly as listed") doi: Pick.ListedDoi, @description("Why it belongs to the goal, in one sentence") why: Pick.Rationale) derives JsonCodec, KindlingsSchema, Schema

object Pick:
  type ListedDoi = ListedDoi.T
  object ListedDoi extends Newtype[String, Pure]
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
