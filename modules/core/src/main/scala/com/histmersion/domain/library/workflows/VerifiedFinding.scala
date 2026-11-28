package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.catalogue.Geocode
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Year
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.AgentVerify.*
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.EnrichSource.Tag
import com.histmersion.api.catalogue.geocode.KindHint
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

final case class VerifiedFinding(
    claim: VerifiedFinding.Claim,
    quote: VerifiedFinding.Quote,
    ratio: AgentVerify.Ratio,
    place: Option[VerifiedFinding.Place],
    start: Option[Year],
    end: Option[Year],
    periodName: Option[VerifiedFinding.PeriodName],
    tags: List[Tag],
    confidence: VerifiedFinding.Confidence,
    placeKind: Option[KindHint] = None
) derives JsonCodec:
  def period: Option[Period] = (start, end) match
    case (Some(s), Some(e)) => Some(Period(s, e))
    case (Some(s), None)    => Some(Period(s, s))
    case (None, Some(e))    => Some(Period(e, e))
    case _                  => None

object VerifiedFinding:
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type Quote = Quote.T
  object Quote extends Newtype[String, Pure]
  type Place = Place.T
  object Place extends Newtype[String, Pure]
  type PeriodName = PeriodName.T
  object PeriodName extends Newtype[String, Pure]
  type Confidence = Confidence.T
  object Confidence extends Newtype[String, Pure]
