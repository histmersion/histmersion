package com.histmersion
package domain.library.search

import com.histmersion.api.library.tree.TreeRow

import com.histmersion.api.library.Tree

import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.util.database.Transactor
import com.histmersion.api.library.{Filter, SearchResult}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place
import com.histmersion.lint.{tolerated, LintRule}

/** The filter's reads over sources: the search, the counts of what a filter selects, and the rows the histogram and its
  * tree bucket. A mechanism over the filter's SQL (full-text ranking and headlines, PostGIS boxes, the gazetteer's
  * scope), so it has no in-memory twin; the SQL is the filter's builders' (`FilterSql`, `AggregateRepo`).
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.Service, "pending G16 (a service is a trait with Implementation and InMemory)")
trait SearchService:
  def sources(filter: Filter, snippetWords: Search.SnippetWords, scope: PlaceScope): SearchResult
  def count(filter: Filter, scope: PlaceScope): RowCount
  def unlocated(filter: Filter, scope: PlaceScope): RowCount
  def selectionCounts(filter: Filter, scope: PlaceScope): Place.Counts
  def periods(unit: AggregateRepo.Unit, filter: Filter, scope: PlaceScope): List[Period.Row]
  def treeRows(unit: AggregateRepo.Unit, filter: Filter, scope: PlaceScope): List[TreeRow]

object SearchService:
  def apply(db: Transactor): SearchService = Implementation(db)

  final private class Implementation(db: Transactor) extends SearchService:
    def sources(filter: Filter, snippetWords: Search.SnippetWords, scope: PlaceScope): SearchResult =
      db.read(SearchRepo.sources(filter, snippetWords, scope))
    def count(filter: Filter, scope: PlaceScope): RowCount = db.read(SearchRepo.count(filter, scope))
    def unlocated(filter: Filter, scope: PlaceScope): RowCount = db.read(SearchRepo.unlocated(filter, scope))
    def selectionCounts(filter: Filter, scope: PlaceScope): Place.Counts =
      db.read(SearchRepo.selectionCounts(filter, scope))
    def periods(unit: AggregateRepo.Unit, filter: Filter, scope: PlaceScope): List[Period.Row] =
      db.read(AggregateRepo.periods(unit, filter, scope)).toList
    def treeRows(unit: AggregateRepo.Unit, filter: Filter, scope: PlaceScope): List[TreeRow] =
      db.read(AggregateRepo.treeRows(unit, filter, scope)).toList
