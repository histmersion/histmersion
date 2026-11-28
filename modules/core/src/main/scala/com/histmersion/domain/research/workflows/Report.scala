package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.research.CritiqueReason
import com.histmersion.domain.Proposal
import com.histmersion.domain.research.workflows.Verdict
import com.histmersion.domain.research.workflows.Reviewer
import com.histmersion.domain.research.workflows.SpotCheck
import com.histmersion.domain.research.workflows.Finding
import com.histmersion.domain.research.workflows.Check
import com.histmersion.domain.research.workflows.ColdReaderAnswer
import com.histmersion.domain.ReviewArticle

final case class Report(
    reviewer: Reviewer,
    verdict: Verdict,
    findings: List[Finding],
    explainBack: Option[Report.ExplainBack] = None,
    emptySlots: List[Report.EmptySlots] = Nil,
    proposals: List[Proposal] = Nil,
    checks: List[Check] = Nil,
    spotChecks: List[SpotCheck] = Nil,
    notMaterial: List[CritiqueReason] = Nil
)

object Report:
  export ColdReaderAnswer.ExplainBack
  export ReviewArticle.ExplainSlotsValue as EmptySlots
