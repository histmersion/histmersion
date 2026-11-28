package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.library
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.automation.agents.ToolUse
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.research.workflows.Quotation
import com.histmersion.Newtype
import com.histmersion.domain.ReviewArticle

final case class SpotCheck(
    citekey: library.Citekey,
    claim: SpotCheck.Claim,
    verdict: SpotCheck.Verdict,
    recordQuote: SpotCheck.RecordQuote,
    ratio: SpotCheck.Ratio,
    tool: SpotCheck.Tool
) derives JsonCodec

object SpotCheck:
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  export ReviewArticle.SpotVerdictsValue as Verdict
  export Quotation.Quote as RecordQuote
  export AgentVerify.Ratio
  export ToolUse.Tool
