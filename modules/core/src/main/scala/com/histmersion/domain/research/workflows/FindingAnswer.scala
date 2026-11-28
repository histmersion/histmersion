package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.research.workflows.EvidenceAnswer
import com.histmersion.api.research.CritiqueRef
import com.histmersion.domain.ReviewArticle.MaxEvidence
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Quotation
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.research.workflows.Severity

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class FindingAnswer(
    severity: FindingAnswer.Severity,
    kind: FindingAnswer.Kind,
    where: FindingAnswer.Where,
    quote: FindingAnswer.Quote,
    problem: FindingAnswer.Problem,
    fix: FindingAnswer.Fix,
    evidence: List[EvidenceAnswer] = Nil,
    pair: Option[FindingAnswer.Pair] = None
)

object FindingAnswer:
  export Severity.Wire as Severity
  export Finding.{Kind, Where}
  export Quotation.Quote
  export Finding.{Fix, Problem}
  export CritiqueRef.Wire as Pair
