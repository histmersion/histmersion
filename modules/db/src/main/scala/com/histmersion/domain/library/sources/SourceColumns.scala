package com.histmersion
package domain.library.sources

import com.augustnagro.magnum.*
import com.histmersion.domain.library.Source
import com.histmersion.util.database.Vocab
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the source status column")
private[library] object SourceColumns:
  /** An unknown status is `unassessed` — nobody has judged it. */
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  given given_DbCodec_SourceStatus: DbCodec[Source.Status] =
    Vocab.codec(
      s => Source.Status.values.find(_.wire.value.equalsIgnoreCase(s.value)),
      e => WireEnumValues.Wire(e.wire.value),
      Source.Status.Unassessed
    )
