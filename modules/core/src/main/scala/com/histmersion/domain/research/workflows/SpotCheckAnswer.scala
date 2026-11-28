package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.research.workflows.Quotation
import com.histmersion.Newtype
import com.histmersion.domain.research.workflows.SpotCheck
import com.histmersion.domain.ReviewArticle

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class SpotCheckAnswer(
    citekey: SpotCheckAnswer.Citekey,
    claim: SpotCheckAnswer.Claim,
    verdict: SpotCheckAnswer.Verdict,
    recordQuote: SpotCheckAnswer.RecordQuote
)

object SpotCheckAnswer:
  type Citekey = Citekey.T
  object Citekey extends Newtype[String, Pure]:
    extension (c: Citekey) def normalised: String = c.value.trim.stripPrefix("[[").stripSuffix("]]").trim
  export SpotCheck.Claim
  export ReviewArticle.SpotVerdictsValue as Verdict
  export Quotation.Quote as RecordQuote
