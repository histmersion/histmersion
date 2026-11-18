package com.histmersion
package repo

import com.augustnagro.magnum.{DbCon, SqlLiteral, sql}
import com.augustnagro.magnum.Frag
import com.histmersion.util.database.given

object Uses:

  private val Esc = sql" ESCAPE '\\'"
  private val Cols = sql"id, label"

  def constant: Frag = sql"SELECT 1"

  def collated: Frag = sql"""SELECT label FROM t ORDER BY label COLLATE "C\""""

  def bound(id: String, year: Long, tags: List[String]): Frag =
    sql" AND id = $id AND year < $year AND tag = ANY($tags::text[])"

  def like(p: String): Frag = sql"label ILIKE $p$Esc"

  def table(t: String): Frag = sql"SELECT count(*) FROM ${SqlLiteral(t)}"

  def chained(where: Frag, limit: Long): Frag =
    sql"SELECT $Cols FROM t WHERE ($where) LIMIT $limit"

  def nested(where: Frag)(using DbCon): Int =
    (sql"""WITH x AS (SELECT 1) SELECT count(*) FROM t WHERE $Cols IS NOT NULL""")
      .query[Int]
      .run()
      .size +
      where.query[Int].run().size

  def shown(s: Frag): String = s.sqlString + s.sqlString.toString
