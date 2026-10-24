package com.histmersion.db

import com.augustnagro.magnum.*
import com.histmersion.api.ListFilters
import javax.sql.DataSource

import java.time.Instant

/** The SQL of the list filters (core `ListFilters`): one ` AND …` fragment per filter, named, so a
  * page applies them all and a facet's count applies all but its own. Pure, but for [[facet]]. */
private[db] object ListSql:

  /** The named conditions of one filtered list, in order. */
  final case class Conds(parts: List[(String, Sql)]):
    /** Every condition. */
    def all: Sql = parts.foldLeft(Sql.empty)(_ ++ _._2)
    /** Every condition but the one named `name` — what that facet is counted under. */
    def except(name: String): Sql = parts.filterNot(_._1 == name).foldLeft(Sql.empty)(_ ++ _._2)

  /** ` AND <expr> = ANY(?)` — nothing for no values. */
  def anyOf(expr: String, vs: List[String]): Sql =
    if vs.isEmpty then Sql.empty else Sql.of(s" AND $expr = ANY(?::text[])", Param.A(vs))

  /** ` AND <expr> = ?` — nothing for none. */
  def equal(expr: String, v: Option[String]): Sql =
    v.fold(Sql.empty)(x => Sql.of(s" AND $expr = ?", Param.S(x)))

  /** An `ILIKE` pattern matching `s` anywhere in a value — its own `%`, `_` and `\` taken literally. */
  def contains(s: String): String =
    "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

  /** ` AND <col> >= since AND <col> < until` — either bound left out when unset. */
  def between(col: String, since: Option[Instant], until: Option[Instant]): Sql =
    since.fold(Sql.empty)(s => Sql.of(s" AND $col >= ?::timestamptz", Param.S(s.toString))) ++
      until.fold(Sql.empty)(u => Sql.of(s" AND $col < ?::timestamptz", Param.S(u.toString)))

  /** `SELECT <expr>, <count> <from> … GROUP BY 1`, the largest first (then by value), at most
    * [[ListFilters.FacetMax]] values; NULLs are not a value. `from` = `FROM … WHERE …` with the filters;
    * `count` = what is counted (`count(DISTINCT id)` where a row can appear twice). */
  def facet(ds: DataSource, from: Sql, expr: String, count: String = "count(*)"): List[(String, Int)] =
    connect(ds):
      (Sql.raw(s"SELECT $expr, $count::int ") ++ from ++
        Sql.of(s" AND $expr IS NOT NULL GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT ?", Param.I(ListFilters.FacetMax)))
        .frag.query[(String, Int)].run().toList
