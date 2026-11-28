package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Confidence
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class SourcedClaim(
    citekey: Citekey,
    claim: ClaimAnswer.Claim,
    quote: Proposal.Quote,
    date: Option[ClaimAnswer.Date],
    place: Option[ClaimAnswer.Place],
    confidence: Confidence,
    ratio: SourcedClaim.Ratio
) derives JsonCodec

object SourcedClaim:
  type Ratio = Ratio.T
  object Ratio extends Newtype[Double, Pure]
