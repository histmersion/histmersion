package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.research.workflows.FindingAnswer
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Quotation
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.research.workflows.Severity

final case class ReaderFindingAnswer(
    severity: ReaderFindingAnswer.Severity,
    kind: ReaderFindingAnswer.Kind,
    where: ReaderFindingAnswer.Where,
    quote: ReaderFindingAnswer.Quote,
    problem: ReaderFindingAnswer.Problem,
    fix: ReaderFindingAnswer.Fix
):
  def asFinding: FindingAnswer = FindingAnswer(severity, kind, where, quote, problem, fix)

object ReaderFindingAnswer:
  export Severity.Wire as Severity
  export Finding.{Kind, Where}
  export Quotation.Quote
  export Finding.{Fix, Problem}
