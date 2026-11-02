package com.histmersion.domain.library.search

import com.histmersion.api.library.Tree

import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.util.database.Transactor
import com.histmersion.api.catalogue.{PeriodRow, PlaceCounts}
import com.histmersion.api.library.{Filter, SearchResult}

/** The filter's reads over sources: the search, the counts of what a filter selects, and the rows the histogram and its
  * tree bucket. A mechanism over the filter's SQL (full-text ranking and headlines, PostGIS boxes, the gazetteer's
  * scope), so it has no in-memory twin; the SQL is the filter's builders' (`FilterSql`, `AggregateRepo`).
  */
trait SearchService:
  def sources(filter: Filter, snippetWords: Int, scope: PlaceScope): SearchResult
  def count(filter: Filter, scope: PlaceScope): Int
  def unlocated(filter: Filter, scope: PlaceScope): Int
  def selectionCounts(filter: Filter, scope: PlaceScope): PlaceCounts
  def periods(unit: String, filter: Filter, scope: PlaceScope): List[PeriodRow]
  def treeRows(unit: String, filter: Filter, scope: PlaceScope): List[Tree.TreeRow]

object SearchService:
  def apply(db: Transactor): SearchService = Implementation(db)

  final private class Implementation(db: Transactor) extends SearchService:
    def sources(filter: Filter, snippetWords: Int, scope: PlaceScope): SearchResult =
      db.read(SearchRepo.sources(filter, snippetWords, scope))
    def count(filter: Filter, scope: PlaceScope): Int = db.read(SearchRepo.count(filter, scope))
    def unlocated(filter: Filter, scope: PlaceScope): Int = db.read(SearchRepo.unlocated(filter, scope))
    def selectionCounts(filter: Filter, scope: PlaceScope): PlaceCounts =
      db.read(SearchRepo.selectionCounts(filter, scope))
    def periods(unit: String, filter: Filter, scope: PlaceScope): List[PeriodRow] =
      db.read(AggregateRepo.periods(unit, filter, scope)).toList
    def treeRows(unit: String, filter: Filter, scope: PlaceScope): List[Tree.TreeRow] =
      db.read(AggregateRepo.treeRows(unit, filter, scope)).toList
