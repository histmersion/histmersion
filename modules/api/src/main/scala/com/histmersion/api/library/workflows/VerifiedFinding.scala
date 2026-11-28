package com.histmersion
package api.library.workflows

import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.geocode.KindHint
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Finding.PeriodName
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class VerifiedFinding(
    claim: VerifiedFinding.Claim,
    quote: VerifiedFinding.Quote,
    ratio: VerifiedFinding.Ratio,
    place: Option[VerifiedFinding.Place],
    start: Option[Year],
    end: Option[Year],
    periodName: Option[PeriodName],
    tags: List[VerifiedFinding.Tag],
    confidence: VerifiedFinding.Confidence,
    placeKind: Option[KindHint] = None
) derives JsonCodec

object VerifiedFinding:
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type Quote = Quote.T
  object Quote extends Newtype[String, Pure]
  type Place = Place.T
  object Place extends Newtype[String, Pure]
  type Confidence = Confidence.T
  object Confidence extends Newtype[String, Pure]
  type Ratio = Ratio.T
  object Ratio extends Newtype[Double, Pure]
  type Tag = Tag.T
  object Tag extends Newtype[String, Pure]
