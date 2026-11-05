package com.histmersion.domain.library.search

import com.histmersion.api.library.Tree

import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.util.database.Transactor
import com.histmersion.api.library.{Filter, SearchResult}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Place

/** The filter's reads over sources: the search, the counts of what a filter selects, and the rows the histogram and its
  * tree bucket. A mechanism over the filter's SQL (full-text ranking and headlines, PostGIS boxes, the gazetteer's
  * scope), so it has no in-memory twin; the SQL is the filter's builders' (`FilterSql`, `AggregateRepo`).
  */
trait SearchService:
  def sources(filter: Filter, snippetWords: com.histmersion.domain.library.search.SearchService.SourcesSnippetWords, scope: PlaceScope): SearchResult
  def count(filter: Filter, scope: PlaceScope): com.histmersion.domain.library.search.SearchService.CountResult
  def unlocated(filter: Filter, scope: PlaceScope): com.histmersion.domain.library.search.SearchService.UnlocatedResult
  def selectionCounts(filter: Filter, scope: PlaceScope): Place.Counts
  def periods(unit: com.histmersion.domain.library.search.SearchService.PeriodsUnit, filter: Filter, scope: PlaceScope): List[Period.Row]
  def treeRows(unit: com.histmersion.domain.library.search.SearchService.TreeRowsUnit, filter: Filter, scope: PlaceScope): List[Tree.TreeRow]

object SearchService:
  type SourcesSnippetWords = SourcesSnippetWords.T
  object SourcesSnippetWords extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountResult = CountResult.T
  object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnlocatedResult = UnlocatedResult.T
  object UnlocatedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PeriodsUnit = PeriodsUnit.T
  object PeriodsUnit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TreeRowsUnit = TreeRowsUnit.T
  object TreeRowsUnit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): SearchService = Implementation(db)

  final private class Implementation(db: Transactor) extends SearchService:
    def sources(filter: Filter, snippetWords: com.histmersion.domain.library.search.SearchService.Implementation.SourcesSnippetWords, scope: PlaceScope): SearchResult =
      db.read(SearchRepo.sources(filter, snippetWords, scope))
    def count(filter: Filter, scope: PlaceScope): com.histmersion.domain.library.search.SearchService.Implementation.CountResult = db.read(SearchRepo.count(filter, scope))
    def unlocated(filter: Filter, scope: PlaceScope): com.histmersion.domain.library.search.SearchService.Implementation.UnlocatedResult = db.read(SearchRepo.unlocated(filter, scope))
    def selectionCounts(filter: Filter, scope: PlaceScope): Place.Counts =
      db.read(SearchRepo.selectionCounts(filter, scope))
    def periods(unit: com.histmersion.domain.library.search.SearchService.Implementation.PeriodsUnit, filter: Filter, scope: PlaceScope): List[Period.Row] =
      db.read(AggregateRepo.periods(unit, filter, scope)).toList
    def treeRows(unit: com.histmersion.domain.library.search.SearchService.Implementation.TreeRowsUnit, filter: Filter, scope: PlaceScope): List[Tree.TreeRow] =
      db.read(AggregateRepo.treeRows(unit, filter, scope)).toList

  object Implementation:
    type SourcesSnippetWords = SourcesSnippetWords.T
    object SourcesSnippetWords extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountResult = CountResult.T
    object CountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UnlocatedResult = UnlocatedResult.T
    object UnlocatedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodsUnit = PeriodsUnit.T
    object PeriodsUnit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TreeRowsUnit = TreeRowsUnit.T
    object TreeRowsUnit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
