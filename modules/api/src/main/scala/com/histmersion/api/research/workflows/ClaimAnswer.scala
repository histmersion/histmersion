package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.library.Confidence
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.annotations.description

final case class ClaimAnswer(
    @description("One atomic claim (one fact), in your words, that bears on the article's question") claim: ClaimAnswer.Claim,
    @description("The sentence of the record that states it, copied EXACTLY") quote: Proposal.Quote,
    @description("The date or period the claim is about, as the record gives it; null if none") date: Option[ClaimAnswer.Date] = None,
    @description("The place the claim is about, as the record names it; null if none") place: Option[ClaimAnswer.Place] = None,
    @description("high | medium | low | contested — how firmly the record states it") confidence: Confidence.Wire
) derives JsonCodec, KindlingsSchema, Schema

object ClaimAnswer:
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type Date = Date.T
  object Date extends Newtype[String, Pure]
  type Place = Place.T
  object Place extends Newtype[String, Pure]
