package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import java.time.Instant
import com.histmersion.util.database.ListSql
import com.histmersion.util.database.given
import com.augustnagro.magnum.{sql, Frag}

/** The pure SQL fragments of the list filters (no database). */
class ListSqlSuite extends munit.FunSuite:

  test("a text filter matches anywhere, its own wildcards and backslash taken literally"):
    assertEquals(ListSql.contains("tin"), "%tin%")
    assertEquals(ListSql.contains("50%_off\\"), "%50\\%\\_off\\\\%")

  /** A fragment as what it sends: its text and its bound values, in order (a Frag has no value equality). */
  private def sent(f: Frag): (String, List[Any]) = (f.sqlString, f.params.toList)

  test("a list condition binds ONE array; none is nothing"):
    assertEquals(sent(ListSql.anyOf(sql"kind", Nil)), ("", Nil))
    assertEquals(
      sent(ListSql.anyOf(sql"kind", List("edge", "goal-plan"))),
      (" AND kind = ANY(?::text[])", List(List("edge", "goal-plan")))
    )
    assertEquals(sent(ListSql.equal(sql"j.goal_slug", Some("g"))), (" AND j.goal_slug = ?", List("g")))

  test("a time range is bound as instants; either side may be open"):
    val s = Instant.parse("2026-10-01T00:00:00Z")
    assertEquals(ListSql.between(sql"created_at", Some(s), None).sqlString, " AND created_at >= ?::timestamptz")
    assertEquals(sent(ListSql.between(sql"created_at", None, Some(s)))._2, List("2026-10-01T00:00:00Z"))
    assertEquals(sent(ListSql.between(sql"created_at", None, None)), ("", Nil))

  test("a facet is counted under every condition but its own"):
    val c = ListSql.Conds(
      List("state" -> sql" AND state = 'open'", "kind" -> sql" AND kind = 'edge'")
    )
    assertEquals(c.all.sqlString, " AND state = 'open' AND kind = 'edge'")
    assertEquals(c.except("kind").sqlString, " AND state = 'open'")
    assertEquals(c.except("nothing").sqlString, c.all.sqlString)
