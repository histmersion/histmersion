package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.annotations.description

final case class EvidenceAnswer(
    @description("The source whose record shows it") citekey: EvidenceAnswer.Citekey,
    @description("Where in that source's body the quote is (the get_source offset you read at); null if not from the body") offset: Option[EvidenceAnswer.Offset] = None,
    @description("The sentence of that record, copied EXACTLY from get_source") quote: EvidenceAnswer.Quote
) derives JsonCodec, KindlingsSchema, Schema

object EvidenceAnswer:
  type Citekey = Citekey.T
  object Citekey extends Newtype[String, Pure]
  type Offset = Offset.T
  object Offset extends Newtype[Int, Pure]
  export Quotation.Quote
