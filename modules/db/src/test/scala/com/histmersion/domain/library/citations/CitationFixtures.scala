package com.histmersion.domain.library.citations

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.library.{ContradictionsResult, NeighborRow, SourceGraph}

/** The db suites' reads of the citation graph on a database of their own. */
object CitationFixtures:
  def neighbors(ds: DataSource, citekey: String, kinds: List[String] = Nil): Vector[NeighborRow] =
    connect(ds)(CitationRepo.neighbors(citekey, kinds))
  def graph(ds: DataSource, citekey: String, maxDepth: Int): SourceGraph =
    connect(ds)(CitationRepo.graph(citekey, maxDepth))
  def contradictions(ds: DataSource, citekey: Option[String], since: Option[Long]): ContradictionsResult =
    connect(ds)(CitationRepo.contradictions(citekey, since))
