package com.histmersion
package domain.library.search
import com.histmersion.util.problem.Problem

import com.histmersion.api.library.Tree

import com.histmersion.api.library.Query.{QueryResult, SchemaTable}
import com.histmersion.domain.catalogue.places.PlaceScope

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.library.{Filter, SearchResult}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import com.histmersion.util.time.Timer
import com.histmersion.util.database.Transactor

/** The db suites' reads of the search on a database of their own. */
object SearchFixtures:
  private given Timer = new Timer {
    def start(): Timer.Measure = new Timer.Measure {
      def elapsed: scala.concurrent.duration.FiniteDuration = scala.concurrent.duration.Duration.Zero
    }
  }

  export QueryRepo.{MaxCellChars, MaxRows, Statement}
  export AggregateRepo.Unit as AggregateUnit

  def sources(
      ds: DataSource,
      filter: Filter,
      snippetWords: Search.SnippetWords,
      scope: PlaceScope = PlaceScope.empty
  ): SearchResult =
    connect(ds)(SearchRepo.sources(filter, snippetWords, scope))
  def count(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): RowCount =
    connect(ds)(SearchRepo.count(filter, scope))
  def unlocated(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): RowCount =
    connect(ds)(SearchRepo.unlocated(filter, scope))
  def selectionCounts(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): Place.Counts =
    connect(ds)(SearchRepo.selectionCounts(filter, scope))
  def periods(
      ds: DataSource,
      unit: AggregateRepo.Unit,
      filter: Filter,
      scope: PlaceScope = PlaceScope.empty
  ): Vector[Period.Row] =
    connect(ds)(AggregateRepo.periods(unit, filter, scope))
  def treeRows(
      ds: DataSource,
      unit: AggregateRepo.Unit,
      filter: Filter,
      scope: PlaceScope = PlaceScope.empty
  ): Vector[Tree.TreeRow] =
    connect(ds)(AggregateRepo.treeRows(unit, filter, scope))
  def run(
      ds: DataSource,
      statement: QueryRepo.Statement,
      timeoutMs: Transactor.TimeoutMs,
      maxRows: QueryRepo.MaxRows,
      maxCellChars: QueryRepo.MaxCellChars
  ): Either[Problem, QueryResult] =
    try Transactor(ds).readOnlyRollback(timeoutMs)(QueryRepo.run(statement, maxRows, maxCellChars))
    catch case error: java.sql.SQLException => Left(QueryRepo.message(error))
  def tables(ds: DataSource): List[SchemaTable] = connect(ds)(QueryRepo.tables)
  def periodsQuery(
      unit: AggregateRepo.Unit,
      filter: Filter,
      scope: PlaceScope = PlaceScope.empty
  ): Frag =
    AggregateRepo.periodsQuery(unit, filter, scope)
  def treeRowsQuery(
      unit: AggregateRepo.Unit,
      filter: Filter,
      scope: PlaceScope = PlaceScope.empty
  ): Frag =
    AggregateRepo.treeRowsQuery(unit, filter, scope)
