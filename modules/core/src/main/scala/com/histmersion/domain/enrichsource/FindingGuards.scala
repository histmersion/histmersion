package com.histmersion
package domain.enrichsource

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.catalogue.Geocode
import com.histmersion.api.catalogue.Period
import com.histmersion.api.given
import com.histmersion.api.catalogue.geocode.KindHint
import io.github.iltotore.iron.constraint.any.Pure

final case class FindingGuards(
    minPlacedShare: FindingGuards.MinPlacedShare,
    centuryGapYears: FindingGuards.CenturyGapYears,
    pointerPhrases: List[FindingGuards.PointerPhrases],
    resolves: (FindingGuards.PlaceQuery, KindHint, Option[Period]) => Boolean
)

object FindingGuards:
  export FindingProposal.Place as PlaceQuery

  type MinPlacedShare = MinPlacedShare.T
  object MinPlacedShare extends Newtype[Double, Pure]
  type CenturyGapYears = CenturyGapYears.T
  object CenturyGapYears extends Newtype[Long, Pure]
  type PointerPhrases = PointerPhrases.T
  object PointerPhrases extends Newtype[String, Pure]
