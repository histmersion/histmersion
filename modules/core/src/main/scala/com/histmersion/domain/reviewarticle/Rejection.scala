package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.library
import com.histmersion.domain.ReviewArticle.Rationale
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.reviewarticle.Finding
import com.histmersion.domain.reviewarticle.Quotation

final case class Rejection(
    id: Rejection.Id,
    reason: Rationale,
    citekey: library.Citekey,
    quote: Rejection.Quote,
    ratio: Rejection.Ratio
) derives JsonCodec

object Rejection:
  export Finding.Id
  export Quotation.Quote
  export AgentVerify.Ratio
