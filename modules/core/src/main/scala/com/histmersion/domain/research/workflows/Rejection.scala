package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library
import com.histmersion.domain.ReviewArticle.Rationale
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Quotation

final case class Rejection(
    id: Rejection.Id,
    reason: Rationale,
    citekey: library.Citekey,
    quote: Rejection.Quote,
    ratio: Rejection.Ratio
)

object Rejection:
  export Finding.Id
  export Quotation.Quote
  export AgentVerify.Ratio
