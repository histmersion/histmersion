package com.histmersion.domain.research.articles

import com.histmersion.domain.library.search.FilterSql

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.util.database.{Param, Sql}
import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.api.library.Filter
import com.histmersion.api.research.Coverage

/** What an article's coverage (#958) reads from the corpus: the whole source set of its filter (not a ranked page — no
  * FTS ranking, no snippets), which of a set of citekeys the filter returns, and the disagreement edges touching the
  * cited sources. Every predicate is [[FilterSql.where]], THE source predicate `/api/sources` uses, so "the filter
  * returns N" here and `search` agree. Every method runs on the connection it is given.
  */
private[articles] object CoverageRepo:

  type SourceSetResultPart1 = SourceSetResultPart1.T
  object SourceSetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceSetCap = SourceSetCap.T
  object SourceSetCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MembersResultPart1 = MembersResultPart1.T
  object MembersResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MembersResultPart2 = MembersResultPart2.T
  object MembersResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MembersCitekeys = MembersCitekeys.T
  object MembersCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgesTouchingCitekeys = EdgesTouchingCitekeys.T
  object EdgesTouchingCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EdgesTouchingKinds = EdgesTouchingKinds.T
  object EdgesTouchingKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private given DbCodec[Coverage.Source] = DbCodec.derived
  private given DbCodec[Coverage.Edge] = DbCodec.derived

  import Param.*

  /** How many sources `f` selects, and the first `cap` of them in citekey order. */
  def sourceSet(f: Filter, scope: PlaceScope, cap: com.histmersion.domain.research.articles.CoverageRepo.SourceSetCap)(using DbCon): (com.histmersion.domain.research.articles.CoverageRepo.SourceSetResultPart1, List[Coverage.Source]) =
    val total = FilterSql.countQuery(f, scope).frag.query[Int].run().headOption.getOrElse(0)
    val rows = (Sql.raw("SELECT s.citekey, s.title, s.year, s.status, s.authors FROM sources s WHERE ") ++
      FilterSql.where(f, scope) ++ Sql
        .of(" ORDER BY s.citekey LIMIT ?", I(math.max(0, cap)))).frag.query[Coverage.Source].run().toList
    (total, rows)

  /** How many sources `f` selects, and which of `citekeys` are among them (exact, whatever their number). */
  def members(f: Filter, scope: PlaceScope, citekeys: List[com.histmersion.domain.research.articles.CoverageRepo.MembersCitekeys])(using DbCon): (com.histmersion.domain.research.articles.CoverageRepo.MembersResultPart1, Set[com.histmersion.domain.research.articles.CoverageRepo.MembersResultPart2]) =
    val total = FilterSql.countQuery(f, scope).frag.query[Int].run().headOption.getOrElse(0)
    val in =
      if citekeys.isEmpty then Set.empty[String]
      else
        (Sql.raw("SELECT s.citekey FROM sources s WHERE ") ++ FilterSql.where(f, scope) ++
          Sql.of(" AND s.citekey = ANY(?)", A(citekeys.distinct))).frag.query[String].run().toSet
    (total, in)

  /** The edges of `kinds` with either end in `citekeys` (source to source only, V026). */
  def edgesTouching(citekeys: List[com.histmersion.domain.research.articles.CoverageRepo.EdgesTouchingCitekeys], kinds: List[com.histmersion.domain.research.articles.CoverageRepo.EdgesTouchingKinds])(using DbCon): List[Coverage.Edge] =
    if citekeys.isEmpty || kinds.isEmpty then Nil
    else sql"""SELECT e.src, e.kind, e.dst FROM edges e
              WHERE e.kind = ANY($kinds::text[])
                AND (e.src = ANY(${citekeys.distinct}::text[]) OR e.dst = ANY(${citekeys.distinct}::text[]))
              ORDER BY 1, 2, 3""".query[Coverage.Edge].run().toList
