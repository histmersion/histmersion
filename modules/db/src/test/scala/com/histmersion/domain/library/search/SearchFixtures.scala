package com.histmersion.domain.library.search

import com.histmersion.api.library.search.Tree

import com.histmersion.api.*
import com.histmersion.api.Query.{QueryResult, SchemaTable}
import com.histmersion.domain.catalogue.places.PlaceScope

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The db suites' reads of the search on a database of their own. */
object SearchFixtures:
  def sources(ds: DataSource, filter: Filter, snippetWords: Int, scope: PlaceScope = PlaceScope.empty): SearchResult =
    connect(ds)(SearchRepo.sources(filter, snippetWords, scope))
  def count(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): Int =
    connect(ds)(SearchRepo.count(filter, scope))
  def unlocated(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): Int =
    connect(ds)(SearchRepo.unlocated(filter, scope))
  def selectionCounts(ds: DataSource, filter: Filter, scope: PlaceScope = PlaceScope.empty): PlaceCounts =
    connect(ds)(SearchRepo.selectionCounts(filter, scope))
  def periods(ds: DataSource, unit: String, filter: Filter, scope: PlaceScope = PlaceScope.empty): Vector[PeriodRow] =
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
    connect(ds)(QueryRepo.run(statement, timeoutMs, maxRows, maxCellChars))
  def tables(ds: DataSource): List[SchemaTable] = connect(ds)(QueryRepo.tables)
  def periodsQuery(unit: String, filter: Filter, scope: PlaceScope = PlaceScope.empty): com.histmersion.db.Sql =
    AggregateRepo.periodsQuery(unit, filter, scope)
  def treeRowsQuery(unit: String, filter: Filter, scope: PlaceScope = PlaceScope.empty): com.histmersion.db.Sql =
    AggregateRepo.treeRowsQuery(unit, filter, scope)
