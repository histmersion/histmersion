package com.histmersion
package util.database
import com.augustnagro.magnum.*

import java.time.Instant
import com.histmersion.util.database.NewtypeColumns.given

/** The SQL of the list filters (core `ListFilters`): one ` AND …` fragment per filter, named, so a page applies them
  * all and a facet's count applies all but its own. Pure, but for [[facet]].
  */
private[histmersion] object ListSql:

  /** The named conditions of one filtered list, in order. */
  final case class Conds(parts: List[(String, Frag)]):
    /** Every condition. */
    def all: Frag = parts.map(_._2).joined(sql"")

    /** Every condition but the one named `name` — what that facet is counted under. */
    def except(name: String): Frag = parts.filterNot(_._1 == name).map(_._2).joined(sql"")

  /** ` AND <expr> = ANY(?)` — nothing for no values. */
  def anyOf(expr: String, vs: List[String]): Frag =
    if vs.isEmpty then sql"" else sql" AND ${SqlLiteral(expr)} = ANY($vs::text[])"

  /** ` AND <expr> = ?` — nothing for none. */
  def equal(expr: String, v: Option[String]): Frag =
    v.fold(sql"")(x => sql" AND ${SqlLiteral(expr)} = $x")

  /** An `ILIKE` pattern matching `s` anywhere in a value — its own `%`, `_` and `\` taken literally. */
  def contains(s: String): String =
    "%" + s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

  /** ` AND <col> >= since AND <col> < until` — either bound left out when unset. */
  def between(col: String, since: Option[Instant], until: Option[Instant]): Frag =
    sql"${since.fold(sql"")(s => sql" AND ${SqlLiteral(col)} >= ${s.toString}::timestamptz")}${until.fold(sql"")(u =>
        sql" AND ${SqlLiteral(col)} < ${u.toString}::timestamptz"
      )}"

  /** `SELECT <expr>, <count> <from> … GROUP BY 1`, the largest first (then by value), at most `max` values (the lists'
    * filters give theirs); NULLs are not a value. `from` = `FROM … WHERE …` with the filters; `count` = what is counted
    * (`count(DISTINCT id)` where a row can appear twice).
    */
  def facetOn(from: Frag, expr: String, max: PageSize, count: String = "count(*)")(using
      DbCon
  ): List[(String, RowCount)] =
    sql"SELECT ${SqlLiteral(expr)}, ${SqlLiteral(count)}::int $from AND ${SqlLiteral(expr)} IS NOT NULL GROUP BY 1 ORDER BY 2 DESC, 1 LIMIT $max"
      .query[(String, Int)]
      .run()
      .toList
      .map((v, n) => (v, RowCount(n)))
