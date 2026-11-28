package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.research.CritiquePair
import com.histmersion.api.research.CritiquePairing
import com.histmersion.api.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.reviewarticle.Fix
import com.histmersion.domain.ReviewLedger
import com.histmersion.domain.reviewledger.Regression
import com.histmersion.api.research.critiquepairing.MissedPair

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class FixGuards(
    pairsOf: Fix.Body => List[CritiquePair],
    regressions: (Fix.Body, Fix.Body) => List[Regression],
    repair: Fix.Body => (Fix.Body, List[MissedPair])
)
