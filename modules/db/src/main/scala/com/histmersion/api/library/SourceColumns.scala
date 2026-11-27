package com.histmersion
package api.library

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.Vocab
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Concept, "pending G5 (one concept, one type within a layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the source status column")
private[histmersion] object SourceColumns:
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  given apiStatus: DbCodec[Source.Status] =
    Vocab.codec(
      s => Source.Status.values.find(_.wire.value.equalsIgnoreCase(s.value)),
      e => WireEnumValues.Wire(e.wire.value),
      Source.Status.Unassessed
    )

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  given apiConfidence: DbCodec[Option[Confidence]] = DbCodec[Option[String]].biMap(
    _.flatMap(s => Confidence.fromString(Confidence.Wire(s.trim))),
    _.map(_.wire.value)
  )

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  given apiEdgeKind: DbCodec[Edge.Kind] =
    Vocab.codec(
      s => Edge.Kind.fromWire(Edge.Kind.Wire(s.value)),
      e => WireEnumValues.Wire(Edge.Kind.wireOf(e).value),
      Edge.Kind.References
    )
