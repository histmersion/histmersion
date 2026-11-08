package com.histmersion
package domain.library.citations

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.library.{ContradictionsResult, NeighborRow}
import com.histmersion.api.library.Source
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Edge

/** The db suites' reads of the citation graph on a database of their own. */
object CitationFixtures:
  export CitationRepo.Since

  def neighbors(
      ds: DataSource,
      citekey: Citekey,
      kinds: List[Edge.Kind.Wire] = Nil
  ): Vector[NeighborRow] =
    connect(ds)(CitationRepo.neighbors(citekey, kinds))
  def graph(ds: DataSource, citekey: Citekey, maxDepth: Source.Graph.Depth): Source.Graph =
    connect(ds)(CitationRepo.graph(citekey, maxDepth))
  def contradictions(
      ds: DataSource,
      citekey: Option[Citekey],
      since: Option[CitationRepo.Since]
  ): ContradictionsResult =
    connect(ds)(CitationRepo.contradictions(citekey, since))
