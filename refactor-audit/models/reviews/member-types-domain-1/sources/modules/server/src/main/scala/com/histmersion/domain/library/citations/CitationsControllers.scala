package com.histmersion.domain.library.citations

import com.histmersion.domain.Caller
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.library.{ContradictionsResult, EdgesResolved, NeighborRow}
import com.histmersion.api.Api
import com.histmersion.api.library.Source

/** The citation graph over REST and MCP. Each row's far end arrives classified ([[NeighborTarget]]), so every renderer
  * (MCP, the CLI over HTTP, the HTML page) links sources, articles and unresolved targets without a second lookup. A
  * citekey asked for is resolved first.
  */
final class CitationsControllers(citations: CitationService, sources: SourceService, maintenance: WriteGate):

  private def canonical(id: com.histmersion.domain.library.citations.CitationsControllers.CanonicalId): com.histmersion.domain.library.citations.CitationsControllers.CanonicalResult = sources.current(id).getOrElse(id)

  def neighbors(citekey: com.histmersion.domain.library.Citekey, kinds: List[com.histmersion.domain.library.citations.CitationsControllers.NeighborsKinds] = Nil): List[NeighborRow] =
    citations.neighbors(canonical(citekey), kinds)

  def graph(citekey: com.histmersion.domain.library.Citekey, depth: Option[com.histmersion.domain.library.citations.CitationsControllers.GraphDepth]): Source.Graph =
    citations.graph(canonical(citekey), depth.getOrElse(2).max(0).min(4))

  def contradictions(citekey: Option[com.histmersion.domain.library.Citekey], since: Option[com.histmersion.domain.library.citations.CitationsControllers.ContradictionsSince]): ContradictionsResult =
    citations.contradictions(citekey.map(canonical), since)

  /** `POST /api/edges/resolve`: the parked edges whose target now names one stored source, promoted. */
  def resolveEdges: Either[Api.Error, EdgesResolved] =
    maintenance.guard("edges/resolve"):
      Right(EdgesResolved(sources.resolveEdges(Caller.Local)))

object CitationsControllers:
  type CanonicalResult = CanonicalResult.T
  object CanonicalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CanonicalId = CanonicalId.T
  object CanonicalId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NeighborsKinds = NeighborsKinds.T
  object NeighborsKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GraphDepth = GraphDepth.T
  object GraphDepth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContradictionsSince = ContradictionsSince.T
  object ContradictionsSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
