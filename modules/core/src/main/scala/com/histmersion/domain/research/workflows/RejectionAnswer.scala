package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.ReviewArticle.Rationale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Quotation
import com.histmersion.Newtype

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class RejectionAnswer(
    id: RejectionAnswer.Id,
    reason: Rationale,
    citekey: RejectionAnswer.Citekey,
    quote: RejectionAnswer.Quote
)

object RejectionAnswer:
  type Citekey = Citekey.T
  object Citekey extends Newtype[String, Pure]:
    extension (c: Citekey) def normalised: String = c.value.trim
  export Finding.Id
  export Quotation.Quote
