package com.histmersion
package domain.library.findings

import com.augustnagro.magnum.*
import com.histmersion.domain.library.Confidence
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

@atBoundary(BoundaryKind.Sql, "reads and writes the finding confidence column")
private[library] object FindingColumns:
  /** `findings.confidence`: NULL and an unknown word are both "not stated". There is deliberately no
    * `DbCodec[Confidence]` — a column read as a bare confidence would need a default, and every default claims
    * something.
    */
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  given DbCodec[Option[Confidence]] = DbCodec[Option[String]].biMap(
    _.flatMap(s => Confidence.fromString(Confidence.Wire(s.trim))),
    _.map(_.wire.value)
  )
