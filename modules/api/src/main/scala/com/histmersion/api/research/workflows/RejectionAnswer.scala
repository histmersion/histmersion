package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.annotations.description

final case class RejectionAnswer(
    @description("The id of the finding you reject (R2, S1…)") id: RejectionAnswer.Id,
    reason: RejectionAnswer.Rationale,
    @description("The source whose record contradicts the finding") citekey: RejectionAnswer.Citekey,
    @description("The sentence of that record that contradicts it, copied EXACTLY (from get_source)") quote: RejectionAnswer.Quote
) derives JsonCodec, KindlingsSchema, Schema

object RejectionAnswer:
  type Citekey = Citekey.T
  object Citekey extends Newtype[String, Pure]
  export ReviewFinding.Id
  export Quotation.Quote
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
