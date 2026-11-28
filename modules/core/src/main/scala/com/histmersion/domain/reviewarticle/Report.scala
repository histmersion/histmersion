package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.research.CritiqueReason
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.Proposal
import com.histmersion.domain.reviewarticle.Verdict
import com.histmersion.domain.reviewarticle.Reviewer
import com.histmersion.domain.reviewarticle.SpotCheck
import com.histmersion.domain.reviewarticle.Finding
import com.histmersion.domain.reviewarticle.Check
import com.histmersion.domain.reviewarticle.ColdReaderAnswer
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
) derives JsonCodec

object Report:
  export ColdReaderAnswer.ExplainBack
  export ReviewArticle.ExplainSlotsValue as EmptySlots
