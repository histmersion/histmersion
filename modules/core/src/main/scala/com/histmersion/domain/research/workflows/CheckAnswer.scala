package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Check
import com.histmersion.domain.research.workflows.Quotation

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CheckAnswer(
    id: CheckAnswer.Id,
    fixed: CheckAnswer.Fixed,
    quote: CheckAnswer.Quote,
    note: CheckAnswer.Note
)

object CheckAnswer:
  export Finding.Id
  export Check.Fixed
  export Quotation.Quote
  export Check.Note
