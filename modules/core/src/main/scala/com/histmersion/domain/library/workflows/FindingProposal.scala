package com.histmersion
package domain.library.workflows

import com.histmersion.api.catalogue.Geocode
import com.histmersion.domain.library
import com.histmersion.domain.EnrichSource.Tag
import com.histmersion.domain.Proposal
import com.histmersion.api.catalogue.geocode.KindHint
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
)

object FindingProposal:
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type Place = Place.T
  object Place extends Newtype[String, Pure]
  type PeriodName = PeriodName.T
  object PeriodName extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def pointerPhrase(
      claim: Claim,
      phrases: List[FindingGuards.PointerPhrases]
  ): Option[FindingGuards.PointerPhrases] =
    val lower = claim.value.toLowerCase(java.util.Locale.ROOT)
    phrases.find(p =>
      s"(?<![\\p{L}\\p{N}])${java.util.regex.Pattern.quote(p.value)}(?![\\p{L}\\p{N}])".r.findFirstIn(lower).isDefined
    )
