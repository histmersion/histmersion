package com.histmersion.api

import com.histmersion.api.library.Query.*
import com.histmersion.api.library.Query

/** The pure half of the read-only query surface: the Markdown table and the generated tool description (plan §5.2
  * stream 2.8). Execution is the search's `QueryRepo`, tested against Postgres.
  */
class QuerySuite extends munit.FunSuite:

  private def row(vs: String*): List[Option[String]] = vs.toList.map(v => if v == null then None else Some(v))

  test("a table renders header, rule and rows"):
    val md = markdownTable(List("citekey", "n"), List(row("a", "1"), row("b", "2")))
    assertEquals(md, "| citekey | n |\n|---|---|\n| a | 1 |\n| b | 2 |")

  test("NULL renders as NULL, not as an empty cell that reads like an empty string"):
    assertEquals(markdownTable(List("x"), List(List(None))), "| x |\n|---|\n| NULL |")

  test("a pipe in the data is escaped so it cannot break the table"):
    val md = markdownTable(List("a|b"), List(row("x|y")))
    assert(md.contains("| a\\|b |"), md)
    assert(md.contains("| x\\|y |"), md)

  test("a multi-line cell is flattened onto one line"):
    assertEquals(markdownTable(List("t"), List(row("one\ntwo"))), "| t |\n|---|\n| one two |")

  test("a short row is padded rather than shifting the columns"):
    assertEquals(markdownTable(List("a", "b"), List(List(Some("1")))), "| a | b |\n|---|---|\n| 1 | NULL |")

  test("no columns is a note, not a broken table"):
    assertEquals(markdownTable(Nil, Nil), "_(no columns)_")

  test("the result header carries the row count, the truncation flag and the timing"):
    val md = Query.markdown(QueryResult(List("n"), List(row("209")), 1, false, 7))
    assert(md.startsWith("### 1 row(s) · 7 ms"), md)
    val cut = Query.markdown(QueryResult(List("n"), List(row("1")), 1, true, 7))
    assert(cut.contains("truncated by the row cap"), cut)

  test("an empty result says 'no rows' instead of rendering an empty table"):
    assert(Query.markdown(QueryResult(List("n"), Nil, 0, false, 1)).endsWith("_no rows_"))

  // ── the generated tool description ───────────────────────────────────────────────────────────

  private val schema = SchemaTables(
    List(
      SchemaTable(
        "sources",
        "table",
        List(
          SchemaColumn("citekey", "text", false),
          SchemaColumn("title", "text", false),
          SchemaColumn("ocr_markdown", "text", true)
        )
      ),
      SchemaTable(
        "queue_items",
        "table",
        List(
          SchemaColumn("id", "integer", false),
          SchemaColumn("list", "text", false),
          SchemaColumn("text", "text", false)
        )
      )
    )
  )

  test("the tool description names the tables that actually exist, with their key columns"):
    val d = Query.toolDescription(schema)
    assert(d.contains("sources(citekey, title)"), d)
    assert(d.contains("queue_items(id, list, text)"), d)
    assert(!d.contains("ocr_markdown"), "non-key columns stay out of the description")
    assert(d.contains("READ ONLY"), d)
    assert(d.contains("BCE negative"), "the deep-time convention is stated where a model will read it")

  test("an unreadable schema still yields a usable description"):
    val d = Query.toolDescription(SchemaTables(Nil))
    assert(d.contains("schema unavailable"), d)
    assert(d.contains("READ ONLY"), d)

  test("the schema listing renders every table with its columns and types"):
    val md = Query.schemaMarkdown(schema)
    assert(md.startsWith("### 2 table(s)"), md)
    assert(md.contains("- **sources** (table) — citekey text, title text, ocr_markdown text"), md)
    assertEquals(Query.schemaMarkdown(SchemaTables(Nil)), "_(no tables)_")

  // ── the statement check (the critical read-only guard, layer 1) ──────────────────────────────
  // pgjdbc splits a `;`-separated batch and sends each part, so `SET TRANSACTION READ WRITE; DELETE
  // …` used to run BOTH — the first part disarming the guard the second was supposed to hit. These
  // tests pin the scanner that makes that impossible.

  test("one statement is one statement, trailing ';' and all"):
    assertEquals(Query.statements("select 1"), List("select 1"))
    assertEquals(Query.statements("select 1;"), List("select 1"))
    assertEquals(Query.statements("  select 1 ;  "), List("select 1"))

  test("a ';'-separated batch is seen as several statements"):
    assertEquals(Query.statements("select 1; select 2"), List("select 1", "select 2"))
    assertEquals(Query.statements("SET TRANSACTION READ WRITE; select 1").size, 2)

  test("a ';' inside a string literal is DATA, not a separator"):
    assertEquals(Query.statements("select ';' as x"), List("select ';' as x"))
    assertEquals(Query.statements("select 'a;b' || 'c;d'"), List("select 'a;b' || 'c;d'"))

  test("a doubled quote inside a literal does not end it"):
    assertEquals(Query.statements("select 'it''s; fine'"), List("select 'it''s; fine'"))

  test("a ';' inside a quoted identifier is not a separator"):
    assertEquals(Query.statements("""select "od;d" from t"""), List("""select "od;d" from t"""))

  test("a ';' inside a dollar-quoted body is not a separator"):
    assertEquals(Query.statements("select $$a;b$$"), List("select $$a;b$$"))
    assertEquals(Query.statements("select $tag$a;b$tag$ , 1"), List("select $tag$a;b$tag$ , 1"))

  test("a ';' inside a comment is not a separator"):
    assertEquals(Query.statements("select 1 -- ;drop\n , 2").size, 1)
    assertEquals(Query.statements("select 1 /* ; */ , 2").size, 1)

  test("nested block comments close in the right order"):
    assertEquals(Query.statements("select /* a /* b ; */ c ; */ 1").size, 1)

  // ── check ────────────────────────────────────────────────────────────────────────────────────

  test("a plain SELECT passes and comes back trimmed"):
    assertEquals(Query.check("  select count(*) from sources  "), Right("select count(*) from sources"))

  test("SET TRANSACTION READ WRITE; SELECT … is REFUSED (the disarming batch)"):
    val e = Query.check("SET TRANSACTION READ WRITE; select * from sources").left.toOption.get
    assert(e.contains("2 statements"), e)

  test("SELECT 1; SELECT 2 is refused — one statement means one"):
    assert(Query.check("select 1; select 2").isLeft)

  test("a lone session/transaction-control statement is refused by keyword"):
    List(
      "SET TRANSACTION READ WRITE",
      "set statement_timeout = 0",
      "BEGIN",
      "commit",
      "ROLLBACK",
      "RESET ALL",
      "DO $$ BEGIN END $$",
      "CALL something()",
      "discard all"
    )
      .foreach(s => assert(Query.check(s).isLeft, s"'$s' was NOT refused"))

  test("the keyword check sees past leading comments"):
    assert(Query.check("-- innocent\nSET TRANSACTION READ WRITE").isLeft)
    assert(Query.check("/* hi */ set statement_timeout = 0").isLeft)
    assertEquals(Query.check("-- fine\nselect 1"), Right("-- fine\nselect 1"))

  test("a WITH that only reads is allowed through to Postgres (which judges it)"):
    assert(Query.check("with x as (select 1) select * from x").isRight)

  test("empty stays empty"):
    assertEquals(Query.check("   "), Left("empty statement"))
    assertEquals(Query.check(" ; ; "), Left("empty statement"))
