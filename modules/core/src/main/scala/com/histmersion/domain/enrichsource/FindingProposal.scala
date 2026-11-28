package com.histmersion
package domain.enrichsource

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.catalogue.Geocode
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.library
import com.histmersion.domain.EnrichSource.Tag
import com.histmersion.domain.Proposal
import com.histmersion.api.catalogue.geocode.KindHint
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

final case class FindingProposal(
    claim: FindingProposal.Claim,
    quote: Proposal.Quote,
    place: Option[FindingProposal.Place],
    placeKind: Option[KindHint.Wire],
    start: Option[StatedYear],
    end: Option[StatedYear],
    periodName: Option[FindingProposal.PeriodName],
    tags: List[Tag] = Nil,
    confidence: library.Confidence.Wire
) derives JsonCodec

object FindingProposal:
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type Place = Place.T
  object Place extends Newtype[String, Pure]
  type PeriodName = PeriodName.T
  object PeriodName extends Newtype[String, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[FindingProposal] = KindlingsSchema
    .derived[FindingProposal]
    .schema
    .modify(_.claim)(_.description("One claim the source makes, in your words, one sentence"))
    .modify(_.quote)(_.description("The sentence(s) of the text that state it, copied EXACTLY, 8-60 words"))
    .modify(_.place)(_.description("The place the claim is about, as a gazetteer would name it; null if none"))
    .modify(_.placeKind)(
      _.description(
        "What kind of place `place` is: site (a city, settlement or excavated site) | region (a country, landscape or province) | polity (a state, kingdom or empire) | water (a sea, lake or river) | unknown; null if no place"
      )
    )
    .modify(_.confidence)(_.description("high | medium | low | contested — how firmly the SOURCE states it"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pointerPhrase(
      claim: FindingProposal.Claim,
      phrases: List[FindingGuards.PointerPhrases]
  ): Option[FindingGuards.PointerPhrases] =
    val lower = claim.value.toLowerCase(java.util.Locale.ROOT)
    phrases.find(p =>
      s"(?<![\\p{L}\\p{N}])${java.util.regex.Pattern.quote(p.value)}(?![\\p{L}\\p{N}])".r.findFirstIn(lower).isDefined
    )
