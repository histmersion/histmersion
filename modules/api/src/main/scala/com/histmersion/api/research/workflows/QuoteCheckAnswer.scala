package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.customise
import sttp.tapir.Schema.annotations.description

@customise(_.name(SName("SpotCheckAnswer")))
final case class QuoteCheckAnswer(
    @description("The cited source, read with get_source or passages in this step") citekey: QuoteCheckAnswer.Citekey,
    @description("The article's words, copied exactly") claim: QuoteCheckAnswer.Claim,
    @description("attributable | extrapolatory | contradictory") verdict: QuoteCheckAnswer.Verdict,
    @description("The record's sentence it rests on, copied EXACTLY") recordQuote: QuoteCheckAnswer.RecordQuote
) derives JsonCodec, KindlingsSchema, Schema

object QuoteCheckAnswer:
  type Citekey = Citekey.T
  object Citekey extends Newtype[String, Pure]
  export CheckedQuote.Claim
  export Quotation.Quote as RecordQuote
  type Verdict = Verdict.SpotVerdictsValue
  object Verdict:
    type SpotVerdictsValue = SpotVerdictsValue.T
    object SpotVerdictsValue extends Newtype[String, Pure]
