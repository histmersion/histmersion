package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.research.CritiqueReason
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class Report(
    reviewer: Reviewer,
    verdict: PanelVerdict,
    findings: List[ReviewFinding],
    explainBack: Option[Report.ExplainBack] = None,
    emptySlots: List[Report.EmptySlots] = Nil,
    proposals: List[Proposal] = Nil,
    checks: List[CheckedFinding] = Nil,
    spotChecks: List[CheckedQuote] = Nil,
    notMaterial: List[CritiqueReason] = Nil
) derives JsonCodec

object Report:
  export ColdReaderAnswer.ExplainBack
  type EmptySlots = EmptySlots.ExplainSlotsValue
  object EmptySlots:
    type ExplainSlotsValue = ExplainSlotsValue.T
    object ExplainSlotsValue extends Newtype[String, Pure]
