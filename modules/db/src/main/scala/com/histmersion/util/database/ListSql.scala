package com.histmersion.util.database
import com.augustnagro.magnum.*

import java.time.Instant

/** The SQL of the list filters (core `ListFilters`): one ` AND …` fragment per filter, named, so a page applies them
  * all and a facet's count applies all but its own. Pure, but for [[facet]].
  */
private[histmersion] object ListSql:

  /** The named conditions of one filtered list, in order. */
  type AnyOfExpr = AnyOfExpr.T
  object AnyOfExpr extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnyOfVs = AnyOfVs.T
  object AnyOfVs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EqualExpr = EqualExpr.T
  object EqualExpr extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EqualV = EqualV.T
  object EqualV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContainsResult = ContainsResult.T
  object ContainsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContainsS = ContainsS.T
  object ContainsS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BetweenCol = BetweenCol.T
  object BetweenCol extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetOnResultPart1 = FacetOnResultPart1.T
  object FacetOnResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetOnResultPart2 = FacetOnResultPart2.T
  object FacetOnResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetOnExpr = FacetOnExpr.T
  object FacetOnExpr extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetOnMax = FacetOnMax.T
  object FacetOnMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetOnCount = FacetOnCount.T
  object FacetOnCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Conds(parts: List[(Conds.PartsPart1, Sql)]):
    /** Every condition. */
    def all: Sql = parts.foldLeft(Sql.empty)(_ ++ _._2)

    /** Every condition but the one named `name` — what that facet is counted under. */
    def except(name: com.histmersion.util.database.ListSql.Conds.ExceptName): Sql = parts.filterNot(_._1 == name).foldLeft(Sql.empty)(_ ++ _._2)

  object Conds:
    type ExceptName = ExceptName.T
    object ExceptName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type PartsPart1 = PartsPart1.T
    object PartsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** ` AND <expr> = ANY(?)` — nothing for no values. */
  def anyOf(expr: com.histmersion.util.database.ListSql.AnyOfExpr, vs: List[com.histmersion.util.database.ListSql.AnyOfVs]): Sql =
    if vs.isEmpty then Sql.empty else Sql.of(s" AND $expr = ANY(?::text[])", Param.A(vs))

  /** ` AND <expr> = ?` — nothing for none. */
  def equal(expr: com.histmersion.util.database.ListSql.EqualExpr, v: Option[com.histmersion.util.database.ListSql.EqualV]): Sql =
    v.fold(Sql.empty)(x => Sql.of(s" AND $expr = ?", Param.S(x)))

  /** An `ILIKE` pattern matching `s` anywhere in a value — its own `%`, `_` and `\` taken literally. */
  def contains(s: com.histmersion.util.database.ListSql.ContainsS): com.histmersion.util.database.ListSql.ContainsResult =
    "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

  /** ` AND <col> >= since AND <col> < until` — either bound left out when unset. */
  def between(col: com.histmersion.util.database.ListSql.BetweenCol, since: Option[Instant], until: Option[Instant]): Sql =
    since.fold(Sql.empty)(s => Sql.of(s" AND $col >= ?::timestamptz", Param.S(s.toString))) ++
      until.fold(Sql.empty)(u => Sql.of(s" AND $col < ?::timestamptz", Param.S(u.toString)))

  /** `SELECT <expr>, <count> <from> … GROUP BY 1`, the largest first (then by value), at most `max` values (the lists'
    * filters give theirs); NULLs are not a value. `from` = `FROM … WHERE …` with the filters; `count` = what is counted
    * (`count(DISTINCT id)` where a row can appear twice).
    */
  def facetOn(from: Sql, expr: com.histmersion.util.database.ListSql.FacetOnExpr, max: com.histmersion.util.database.ListSql.FacetOnMax, count: com.histmersion.util.database.ListSql.FacetOnCount = "count(*)")(using DbCon): List[(com.histmersion.util.database.ListSql.FacetOnResultPart1, com.histmersion.util.database.ListSql.FacetOnResultPart2)] =
    (Sql.raw(s"SELECT $expr, $count::int ") ++ from ++
      Sql.of(s" AND $expr IS NOT NULL GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT ?", Param.I(max))).frag
      .query[(String, Int)]
      .run()
      .toList
