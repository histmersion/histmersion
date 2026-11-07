package com.histmersion.db

import com.histmersion.testsupport.ComparedByValue.given

import java.time.Instant
import com.histmersion.util.database.{ListSql, Param, Sql}
import com.histmersion.util.database.given

/** The pure SQL fragments of the list filters (no database). */
class ListSqlSuite extends munit.FunSuite:

  test("a text filter matches anywhere, its own wildcards and backslash taken literally"):
    assertEquals(ListSql.contains("tin"), "%tin%")
    assertEquals(ListSql.contains("50%_off\\"), "%50\\%\\_off\\\\%")

  test("a list condition binds ONE array; none is nothing"):
    assertEquals(ListSql.anyOf("kind", Nil), Sql.empty)
    assertEquals(
      ListSql.anyOf("kind", List("edge", "goal-plan")),
      Sql(com.histmersion.util.database.Sql.Text(" AND kind = ANY(?::text[])"), List(Param.A(List(com.histmersion.util.database.Param.A.V("edge"), com.histmersion.util.database.Param.A.V("goal-plan")))))
    )
    assertEquals(ListSql.equal("j.goal_slug", Some("g")), Sql(com.histmersion.util.database.Sql.Text(" AND j.goal_slug = ?"), List(Param.S(com.histmersion.util.database.Param.S.V("g")))))

  test("a time range is bound as instants; either side may be open"):
    val s = Instant.parse("2026-10-01T00:00:00Z")
    assertEquals(ListSql.between("created_at", Some(s), None).text, " AND created_at >= ?::timestamptz")
    assertEquals(ListSql.between("created_at", None, Some(s)).params, List(Param.S(com.histmersion.util.database.Param.S.V("2026-10-01T00:00:00Z"))))
    assertEquals(ListSql.between("created_at", None, None), Sql.empty)

  test("a facet is counted under every condition but its own"):
    val c = ListSql.Conds(List("state" -> Sql.raw(Sql.Text(" AND state = 'open'")), "kind" -> Sql.raw(Sql.Text(" AND kind = 'edge'"))))
    assertEquals(c.all.text, " AND state = 'open' AND kind = 'edge'")
    assertEquals(c.except("kind").text, " AND state = 'open'")
    assertEquals(c.except("nothing").text, c.all.text)
