package com.histmersion
package domain.research.articles

import com.histmersion.domain.library.search.FilterSql

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given

import com.augustnagro.magnum.*
import com.histmersion.api.library.Filter
import com.histmersion.api.research.Coverage
import com.histmersion.api.library.SourceColumns.given
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Edge
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** What an article's coverage (#958) reads from the corpus: the whole source set of its filter (not a ranked page — no
  * FTS ranking, no snippets), which of a set of citekeys the filter returns, and the disagreement edges touching the
  * cited sources. Every predicate is [[FilterSql.where]], THE source predicate `/api/sources` uses, so "the filter
  * returns N" here and `search` agree. Every method runs on the connection it is given.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the coverage tables and their row codecs")
private[articles] object CoverageRepo:

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[Coverage.Source] = DbCodec.derived
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  private given DbCodec[Coverage.Edge] = DbCodec.derived

  /** How many sources `f` selects, and the first `cap` of them in citekey order. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def sourceSet(f: Filter, scope: PlaceScope, cap: ArticleRepo.Limit)(using DbCon): (RowCount, List[Coverage.Source]) =
    val total = RowCount(FilterSql.countQuery(f, scope).query[Int].run().headOption.getOrElse(0))
    val rows =
      sql"SELECT s.citekey, s.title, s.year, s.status, s.authors FROM sources s WHERE ${FilterSql.where(f, scope)} ORDER BY s.citekey LIMIT ${math.max(0, cap.value)}"
        .query[Coverage.Source]
        .run()
        .toList
    (total, rows)

  /** How many sources `f` selects, and which of `citekeys` are among them (exact, whatever their number). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def members(f: Filter, scope: PlaceScope, citekeys: List[Citekey])(using DbCon): (RowCount, Set[Citekey]) =
    val total = RowCount(FilterSql.countQuery(f, scope).query[Int].run().headOption.getOrElse(0))
    val in =
      if citekeys.isEmpty then Set.empty[Citekey]
      else
        sql"SELECT s.citekey FROM sources s WHERE ${FilterSql.where(f, scope)} AND s.citekey = ANY(${citekeys.distinct})"
          .query[Citekey]
          .run()
          .toSet
    (total, in)

  /** The edges of `kinds` with either end in `citekeys` (source to source only, V026). */
  @tolerated(LintRule.TotalOrder, "columns 1, 2, 3 are src, kind, dst: the edges' key")
  def edgesTouching(citekeys: List[Citekey], kinds: List[Edge.Kind.Wire])(using DbCon): List[Coverage.Edge] =
    if citekeys.isEmpty || kinds.isEmpty then Nil
    else sql"""SELECT e.src, e.kind, e.dst FROM edges e
              WHERE e.kind = ANY($kinds::text[])
                AND (e.src = ANY(${citekeys.distinct}::text[]) OR e.dst = ANY(${citekeys.distinct}::text[]))
              ORDER BY 1, 2, 3""".query[Coverage.Edge].run().toList
