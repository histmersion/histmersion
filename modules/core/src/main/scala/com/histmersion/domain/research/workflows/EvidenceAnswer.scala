package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.research.workflows.Quotation
import com.histmersion.Newtype

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class EvidenceAnswer(
    citekey: EvidenceAnswer.Citekey,
    offset: Option[EvidenceAnswer.Offset] = None,
    quote: EvidenceAnswer.Quote
)

object EvidenceAnswer:
  type Citekey = Citekey.T
  object Citekey extends Newtype[String, Pure]:
    extension (c: Citekey) def normalised: String = c.value.trim.stripPrefix("[[").stripSuffix("]]").trim
  type Offset = Offset.T
  object Offset extends Newtype[Int, Pure]
  export Quotation.Quote
