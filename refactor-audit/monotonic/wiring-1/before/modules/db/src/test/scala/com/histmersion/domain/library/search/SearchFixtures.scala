package com.histmersion.domain.library.search

import com.histmersion.api.library.Tree

import com.histmersion.api.library.Query.{QueryResult, SchemaTable}
import com.histmersion.domain.catalogue.places.PlaceScope

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.api.library.{Filter, SearchResult}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place

/** The db suites' reads of the search on a database of their own. */
object SearchFixtures:
  def sources(ds: DataSource, filter: Filter, snippetWords: Int, scope: PlaceScope = PlaceScope.empty): SearchResult =
    connect(ds)(SearchRepo.sources(filter, snippetWords, scope))
  def count(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): Int =
    connect(ds)(SearchRepo.count(filter, scope))
  def unlocated(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): Int =
    connect(ds)(SearchRepo.unlocated(filter, scope))
  def selectionCounts(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): Place.Counts =
    connect(ds)(SearchRepo.selectionCounts(filter, scope))
  def periods(ds: DataSource, unit: String, filter: Filter, scope: PlaceScope = PlaceScope.empty): Vector[Period.Row] =
    connect(ds)(AggregateRepo.periods(unit, filter, scope))
  def treeRows(
      ds: DataSource,
      unit: String,
      filter: Filter,
      scope: PlaceScope = PlaceScope.empty
  ): Vector[Tree.TreeRow] =
    connect(ds)(AggregateRepo.treeRows(unit, filter, scope))
  def run(
      ds: DataSource,
      statement: String,
      timeoutMs: Int,
      maxRows: Int,
      maxCellChars: Int
  ): Either[String, QueryResult] =
    try _root_.com.histmersion.util.database.Transactor(ds).readOnlyRollback(timeoutMs)(QueryRepo.run(statement, maxRows, maxCellChars))
    catch case error: java.sql.SQLException => Left(QueryRepo.message(error))
  def tables(ds: DataSource): List[SchemaTable] = connect(ds)(QueryRepo.tables)
  def periodsQuery(
      unit: String,
      filter: Filter,
      scope: PlaceScope = PlaceScope.empty
  ): com.histmersion.util.database.Sql =
    AggregateRepo.periodsQuery(unit, filter, scope)
  def treeRowsQuery(
      unit: String,
      filter: Filter,
      scope: PlaceScope = PlaceScope.empty
  ): com.histmersion.util.database.Sql =
    AggregateRepo.treeRowsQuery(unit, filter, scope)
