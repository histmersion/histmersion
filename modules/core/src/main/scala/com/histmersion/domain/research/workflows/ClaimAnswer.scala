package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.library
import com.histmersion.domain.Proposal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class ClaimAnswer(
    claim: ClaimAnswer.Claim,
    quote: Proposal.Quote,
    date: Option[ClaimAnswer.Date] = None,
    place: Option[ClaimAnswer.Place] = None,
    confidence: library.Confidence.Wire
)

object ClaimAnswer:
  type Claim = Claim.T
  object Claim extends Newtype[String, Pure]
  type Date = Date.T
  object Date extends Newtype[String, Pure]
  type Place = Place.T
  object Place extends Newtype[String, Pure]
