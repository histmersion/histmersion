package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.library
import com.histmersion.domain.AgentVerify
import com.histmersion.domain.Proposal

final case class Quotation(
    citekey: library.Citekey,
    offset: Option[Quotation.Offset],
    quote: Quotation.Quote,
    ratio: Quotation.Ratio
)

object Quotation:
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  type Offset = com.histmersion.Offset
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  val Offset: com.histmersion.Offset.type = com.histmersion.Offset
  export Proposal.Quote
  export AgentVerify.Ratio

  extension (e: Quotation)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def evidenceLine: String =
      val at = e.offset.fold("")(o => s" @$o")
      s"[[${e.citekey}]]$at: \"${e.quote.value.replace("\n", " ").take(400)}\""
