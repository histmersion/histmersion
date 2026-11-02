package com.histmersion.domain.research.articles

import com.histmersion.domain.library.search.FilterSql

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.*
import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.database.given

import com.augustnagro.magnum.*

/** What an article's coverage (#958) reads from the corpus: the whole source set of its filter (not a ranked page — no
  * FTS ranking, no snippets), which of a set of citekeys the filter returns, and the disagreement edges touching the
  * cited sources. Every predicate is [[FilterSql.where]], THE source predicate `/api/sources` uses, so "the filter
  * returns N" here and `search` agree. Every method runs on the connection it is given.
  */
private[articles] object CoverageRepo:

  private given DbCodec[CoverageSource] = DbCodec.derived
  private given DbCodec[CoverageEdge] = DbCodec.derived

  import Param.*

  /** How many sources `f` selects, and the first `cap` of them in citekey order. */
  def sourceSet(f: Filter, scope: PlaceScope, cap: Int)(using DbCon): (Int, List[CoverageSource]) =
    val total = FilterSql.countQuery(f, scope).frag.query[Int].run().headOption.getOrElse(0)
    val rows = (Sql.raw("SELECT s.citekey, s.title, s.year, s.status, s.authors FROM sources s WHERE ") ++
      FilterSql.where(f, scope) ++ Sql
        .of(" ORDER BY s.citekey LIMIT ?", I(math.max(0, cap)))).frag.query[CoverageSource].run().toList
    (total, rows)

  /** How many sources `f` selects, and which of `citekeys` are among them (exact, whatever their number). */
  def members(f: Filter, scope: PlaceScope, citekeys: List[String])(using DbCon): (Int, Set[String]) =
    val total = FilterSql.countQuery(f, scope).frag.query[Int].run().headOption.getOrElse(0)
    val in =
      if citekeys.isEmpty then Set.empty[String]
      else
        (Sql.raw("SELECT s.citekey FROM sources s WHERE ") ++ FilterSql.where(f, scope) ++
          Sql.of(" AND s.citekey = ANY(?)", A(citekeys.distinct))).frag.query[String].run().toSet
    (total, in)

  /** The edges of `kinds` with either end in `citekeys` (source to source only, V026). */
  def edgesTouching(citekeys: List[String], kinds: List[String])(using DbCon): List[CoverageEdge] =
    if citekeys.isEmpty || kinds.isEmpty then Nil
    else sql"""SELECT e.src, e.kind, e.dst FROM edges e
              WHERE e.kind = ANY($kinds::text[])
                AND (e.src = ANY(${citekeys.distinct}::text[]) OR e.dst = ANY(${citekeys.distinct}::text[]))
              ORDER BY 1, 2, 3""".query[CoverageEdge].run().toList
