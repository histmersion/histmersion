package com.histmersion
package domain.library.citations

import com.histmersion.domain.Caller
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.library.{ContradictionsResult, EdgesResolved, NeighborRow}
import com.histmersion.api.Api
import com.histmersion.api.library.Source
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Edge
import com.histmersion.domain.Creator
import com.histmersion.lint.{LintRule, tolerated}

/** The citation graph over REST and MCP. Each row's far end arrives classified ([[NeighborTarget]]), so every renderer
  * (MCP, the CLI over HTTP, the HTML page) links sources, articles and unresolved targets without a second lookup. A
  * citekey asked for is resolved first.
  */
final class CitationsControllers(citations: CitationService, sources: SourceService, maintenance: WriteGate):

  private def canonical(id: Citekey): Citekey = sources.current(id).getOrElse(id)

  def neighbors(citekey: Citekey, kinds: List[Edge.Kind.Wire] = Nil): List[NeighborRow] =
    citations.neighbors(canonical(citekey), kinds)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def graph(citekey: Citekey, depth: Option[CitationService.Depth]): Source.Graph =
    citations.graph(canonical(citekey), CitationService.Depth(depth.fold(2)(_.value).max(0).min(4)))

  def contradictions(citekey: Option[Citekey], since: Option[CitationService.Since]): ContradictionsResult =
    citations.contradictions(citekey.map(canonical), since)

  /** `POST /api/edges/resolve`: the parked edges whose target now names one stored source, promoted. */
  @tolerated(LintRule.G44Conversion, "pending G44 (a controller hands the wire value to the domain's parse, not .value)")
  def resolveEdges(using Creator.New): Either[Api.Error, EdgesResolved] =
    maintenance.guard(WriteGate.OperationName("edges/resolve")):
      Right(EdgesResolved(EdgesResolved.Resolved(sources.resolveEdges(Caller.Local).value)))
