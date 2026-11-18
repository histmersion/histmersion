package com.histmersion
package repo

import com.augustnagro.magnum.{DbCon, SqlLiteral, sql}
import com.histmersion.util.database.{Param, Sql}

object Uses:
  private def raw(text: String): Sql = Sql.raw(Sql.RawText(text))
  private def of(text: String, ps: Param*): Sql = Sql.of(Sql.OfText(text), ps*)
  private def str(x: String): Param = Param.S(Param.S.V(x))

  private val Esc = " ESCAPE '\\'"
  private val Cols = "id, label"

  def constant: Sql = Sql.raw(Sql.Text("SELECT 1"))

  def collated: Sql = Sql.raw(Sql.Text("""SELECT label FROM t ORDER BY label COLLATE "C""""))

  def bound(id: String, year: Long, tags: List[String]): Sql =
    Sql.of(
      Sql.OfText(" AND id = ? AND year < ? AND tag = ANY(?::text[])"),
      Param.S(Param.S.V(id)),
      Param.L(Param.L.V(year)),
      Param.A(tags.map(Param.A.V(_)))
    )

  def like(p: String): Sql = of(s"label ILIKE ?$Esc", str(p))

  def table(t: String): Sql = raw(s"SELECT count(*) FROM $t")

  def chained(where: Sql, limit: Long): Sql =
    raw("SELECT ") ++ raw(Cols) ++ " FROM t WHERE " ++ where.wrap("(", ")") ++ Sql.empty ++
      Sql.of(Sql.Text(" LIMIT ?"), Param.L(Param.L.V(limit)))

  def nested(where: Sql)(using DbCon): Int =
    (raw("WITH x AS (SELECT 1) ") ++ sql"""SELECT count(*) FROM t WHERE ${SqlLiteral(Cols)} IS NOT NULL""")
      .query[Int]
      .run()
      .size +
      where.frag.query[Int].run().size

  def shown(s: Sql): String = s.text.value + s.text.toString

  def chosen(on: Boolean): Sql = raw("SELECT 1" + (if on then " WHERE x" else ""))

  def years: Sql = Sql.of(Sql.Text("y BETWEEN ? AND ?"), Param.L(Param.L.V(-10)), Param.L(Param.L.V(10)))

  val expected: List[Param] = List(Param.L(Param.L.V(-10)), Param.S(Param.S.V("x")))
