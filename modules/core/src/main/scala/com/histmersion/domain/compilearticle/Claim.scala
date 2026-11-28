package com.histmersion
package domain.compilearticle

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library.Citekey
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.library.Confidence
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.compilearticle.ClaimAnswer
import com.histmersion.domain.Proposal

final case class Claim(
    citekey: Citekey,
    claim: ClaimAnswer.Claim,
    quote: Proposal.Quote,
    date: Option[ClaimAnswer.Date],
    place: Option[ClaimAnswer.Place],
    confidence: Confidence,
    ratio: AgentVerify.Ratio
) derives JsonCodec
