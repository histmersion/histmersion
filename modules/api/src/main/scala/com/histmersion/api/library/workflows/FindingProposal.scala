package com.histmersion
package api.library.workflows

import com.histmersion.api.catalogue.geocode.KindHint
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.library.Confidence
import com.histmersion.api.library.Finding.PeriodName
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.annotations.description

final case class FindingProposal(
    @description("One claim the source makes, in your words, one sentence") claim: FindingProposal.Claim,
    @description("The sentence(s) of the text that state it, copied EXACTLY, 8-60 words") quote: FindingProposal.Quote,
    @description("The place the claim is about, as a gazetteer would name it; null if none") place: Option[FindingProposal.Place],
    @description("What kind of place `place` is: site (a city, settlement or excavated site) | region (a country, landscape or province) | polity (a state, kingdom or empire) | water (a sea, lake or river) | unknown; null if no place") placeKind: Option[KindHint.Wire],
    start: Option[StatedYear],
    end: Option[StatedYear],
    periodName: Option[PeriodName],
    tags: List[FindingProposal.Tag] = Nil,
    @description("high | medium | low | contested — how firmly the SOURCE states it") confidence: Confidence.Wire
) derives JsonCodec, KindlingsSchema, Schema

object FindingProposal:
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type Place = Place.T
  object Place extends Newtype[String, Pure]
  type Tag = Tag.T
  object Tag extends Newtype[String, Pure]
  type Quote = Quote.T
  object Quote extends Newtype[String, Pure]
