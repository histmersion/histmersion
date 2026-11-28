package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class CheckedQuote(
    citekey: Citekey,
    claim: CheckedQuote.Claim,
    verdict: CheckedQuote.Verdict,
    recordQuote: CheckedQuote.RecordQuote,
    ratio: CheckedQuote.Ratio,
    tool: CheckedQuote.Tool
) derives JsonCodec

object CheckedQuote:
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  export Quotation.Quote as RecordQuote
  type Verdict = Verdict.SpotVerdictsValue
  object Verdict:
    type SpotVerdictsValue = SpotVerdictsValue.T
    object SpotVerdictsValue extends Newtype[String, Pure]
  type Ratio = Ratio.T
  object Ratio extends Newtype[Double, Pure]
  type Tool = Tool.T
  object Tool extends Newtype[String, Pure]
