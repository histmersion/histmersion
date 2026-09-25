package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `histmersion query "<SQL>"` — the read-only SQL escape hatch (plan §5.1 row `report`, §5.2 stream 2.8).
  *
  * The old workflow reached for `sync_db.py --query` whenever a question did not fit `search` /
  * `facets` / `aggregate`. This is that, against the live store.
  *
  * **Read-only is enforced in THREE layers, because no single one of them holds.**
  *
  *  1. [[check]] (here, pure): exactly ONE top-level statement, and it may not start with a
  *     transaction- or session-control keyword. This layer exists because pgjdbc happily splits a
  *     `;`-separated batch and sends each part — so `SET TRANSACTION READ WRITE; DELETE …` used to
  *     run BOTH parts, the first of which undid the guard the second was supposed to hit.
  *  2. a dedicated **`histmersion_query` login role** (migration `V011__query_role.sql`) that is
  *     `NOSUPERUSER` and holds nothing but `SELECT`, with `default_transaction_read_only = on`
  *     baked into the role. This is the layer that actually contains a statement: the application
  *     role is typically a superuser, for which `pg_read_file()` and friends are wide open.
  *  3. `SET TRANSACTION READ ONLY` + `SET LOCAL statement_timeout` per statement — belt and braces,
  *     and the only layer that still applies if the role could not be provisioned.
  *
  * Everything in this file is pure: the DTOs, the statement check, the Markdown table, and the
  * schema blurb that becomes the MCP tool description. `db.QueryRepo` does the executing.
  */
object Query:

  /** The statement to run. ONE statement — a `;`-separated batch is rejected by [[check]]. */
  final case class QueryRequest(sql: String) derives KindlingsCodecAsObject
  object QueryRequest:
    given Schema[QueryRequest] = KindlingsSchema.derived[QueryRequest].schema

  /** Column names plus rows of stringified cells (NULL = absent), which is the only shape that
    * survives an arbitrary projection through one static JSON schema. `truncated` says the row cap
    * (`query.max-rows`) cut the result short. */
  final case class QueryResult(
      columns: List[String],
      rows: List[List[Option[String]]],
      rowCount: Int,
      truncated: Boolean,
      elapsedMs: Long
  ) derives KindlingsCodecAsObject
  object QueryResult:
    given Schema[QueryResult] = KindlingsSchema.derived[QueryResult].schema

  final case class SchemaColumn(name: String, dataType: String, nullable: Boolean)
      derives KindlingsCodecAsObject
  object SchemaColumn:
    given Schema[SchemaColumn] = KindlingsSchema.derived[SchemaColumn].schema

  final case class SchemaTable(name: String, kind: String, columns: List[SchemaColumn])
      derives KindlingsCodecAsObject
  object SchemaTable:
    given Schema[SchemaTable] = KindlingsSchema.derived[SchemaTable].schema

  /** `GET /api/schema/tables` — read from `information_schema` at request time, never a hand-written
    * list, so it cannot go stale as migrations land. */
  final case class SchemaTables(tables: List[SchemaTable]) derives KindlingsCodecAsObject
  object SchemaTables:
    given Schema[SchemaTables] = KindlingsSchema.derived[SchemaTables].schema

  // ── the statement check (pure) ───────────────────────────────────────────────────────────────

  /** Statements that must never reach the database through this hatch: they change the SESSION or
    * the TRANSACTION rather than reading, and one of them (`SET TRANSACTION READ WRITE`) is exactly
    * how a batch used to disarm the read-only guard before the first `SELECT` ran. `DO`/`CALL` run
    * arbitrary procedural code, which is not a read however it is dressed up. */
  private val Forbidden = Set(
    "SET", "RESET", "BEGIN", "START", "COMMIT", "ROLLBACK", "ABORT", "END",
    "SAVEPOINT", "RELEASE", "DISCARD", "DO", "CALL")

  /** Split SQL into top-level statements, honouring everything that can legally contain a `;`:
    * `'…'` strings (with `''` escapes), `"…"` identifiers, `$tag$…$tag$` dollar quoting, `--` line
    * comments and nestable slash-star block comments (Postgres nests them; Scala does too, which
    * is why this sentence does not spell one out).
    *
    * A naive `split(";")` gets `select ';' as x` wrong in the permissive direction, which is the
    * direction that matters here — so this scanner, and not that.
    *
    * Trailing empty parts (a statement politely terminated with `;`) are dropped, so
    * `select 1;` is ONE statement and `select 1; select 2` is two. */
  def statements(sql: String): List[String] =
    val out   = List.newBuilder[String]
    val cur   = StringBuilder()
    var i     = 0
    val n     = sql.length
    var depth = 0                                   // /* */ nesting level (Postgres nests them)
    def at(k: Int): Char = if k >= 0 && k < n then sql.charAt(k) else 0.toChar
    while i < n do
      val c = sql.charAt(i)
      if depth > 0 then
        if c == '*' && at(i + 1) == '/' then { depth -= 1; cur.append("*/"); i += 2 }
        else if c == '/' && at(i + 1) == '*' then { depth += 1; cur.append("/*"); i += 2 }
        else { cur.append(c); i += 1 }
      else if c == '-' && at(i + 1) == '-' then
        while i < n && sql.charAt(i) != '\n' do { cur.append(sql.charAt(i)); i += 1 }
      else if c == '/' && at(i + 1) == '*' then { depth += 1; cur.append("/*"); i += 2 }
      else if c == '\'' || c == '"' then
        // a quoted run ends at the first unescaped closing quote; a doubled quote is a literal one
        cur.append(c); i += 1
        var closed = false
        while i < n && !closed do
          val q = sql.charAt(i)
          if q == c && at(i + 1) == c then { cur.append(c).append(c); i += 2 }
          else if q == c then { cur.append(c); i += 1; closed = true }
          else { cur.append(q); i += 1 }
      else if c == '$' then
        dollarTag(sql, i) match
          case Some(tag) =>
            val end = sql.indexOf(tag, i + tag.length)
            val to  = if end < 0 then n else end + tag.length
            cur.append(sql.substring(i, to)); i = to
          case None => { cur.append(c); i += 1 }
      else if c == ';' then { out += cur.toString; cur.clear(); i += 1 }
      else { cur.append(c); i += 1 }
    out += cur.toString
    out.result().map(_.trim).filter(_.nonEmpty)

  /** `$$` or `$tag$` starting at `i`, if that is what it is. */
  private def dollarTag(sql: String, i: Int): Option[String] =
    val close = sql.indexOf('$', i + 1)
    if close < 0 then None
    else
      val tag = sql.substring(i + 1, close)
      if tag.isEmpty || (tag.head.isLetter || tag.head == '_') && tag.forall(ch => ch.isLetterOrDigit || ch == '_')
      then Some(sql.substring(i, close + 1))
      else None

  /** The first keyword of a statement, with leading comments and whitespace skipped. */
  def leadingKeyword(statement: String): String =
    var s = statement.trim
    var go = true
    while go do
      if s.startsWith("--") then s = s.dropWhile(_ != '\n').trim
      else if s.startsWith("/*") then
        val end = s.indexOf("*/")
        s = (if end < 0 then "" else s.substring(end + 2)).trim
      else go = false
    s.takeWhile(c => c.isLetter).toUpperCase

  /** The gate every statement passes before it reaches Postgres. `Right` carries the single trimmed
    * statement to execute; `Left` is the reason, phrased for whoever (or whatever) wrote the SQL. */
  def check(sql: String): Either[String, String] =
    val text = sql.trim
    if text.isEmpty then Left("empty statement")
    else
      statements(text) match
        case Nil => Left("empty statement")
        case one :: Nil =>
          val kw = leadingKeyword(one)
          if Forbidden.contains(kw) then
            Left(s"'$kw' is not allowed here — `query` runs ONE read-only statement (SELECT, or a " +
              "WITH that only reads); transaction and session control are refused")
          else Right(one)
        case many =>
          Left(s"${many.size} statements were given — `query` runs exactly ONE; " +
            "drop the ';'-separated batch and ask again")

  // ── rendering (pure) ─────────────────────────────────────────────────────────────────────────

  private def cell(v: Option[String]): String =
    v.getOrElse("NULL").replace("|", "\\|").linesIterator.mkString(" ").trim

  /** A GitHub-flavoured Markdown table. Empty columns (a statement that returned nothing) render as
    * a note rather than a broken table, because an LLM reading `| |\n|---|` learns nothing. */
  def markdownTable(columns: List[String], rows: List[List[Option[String]]]): String =
    if columns.isEmpty then "_(no columns)_"
    else
      val header = columns.map(_.replace("|", "\\|")).mkString("| ", " | ", " |")
      val rule   = columns.map(_ => "---").mkString("|", "|", "|")
      val body   = rows.map(r => columns.indices.map(i => cell(r.lift(i).flatten)).mkString("| ", " | ", " |"))
      // NB: no stripMargin — every row starts with '|', which stripMargin would eat.
      (List(header, rule) ++ body).mkString("\n")

  def markdown(r: QueryResult): String =
    val head = s"### ${r.rowCount} row(s)${if r.truncated then " (truncated by the row cap)" else ""} · ${r.elapsedMs} ms"
    if r.rows.isEmpty then s"$head\n\n_no rows_"
    else s"$head\n\n${markdownTable(r.columns, r.rows)}"

  /** The columns worth naming in a tool description: the identity/join columns an LLM needs to write
    * a correct query, not all 24 of `sources`. Everything else is one `GET /api/schema/tables` away. */
  private val Key = Set("id", "citekey", "src", "dst", "kind", "list", "status", "title", "doi",
    "year", "topics", "regions", "tags", "period", "claim", "region_name", "geom", "ord", "topic",
    "slug", "created_at", "done_at", "text", "name", "label")

  def schemaMarkdown(s: SchemaTables): String =
    if s.tables.isEmpty then "_(no tables)_"
    else
      val rows = s.tables.map { t =>
        val cols = t.columns.map(c => s"${c.name} ${c.dataType}").mkString(", ")
        s"- **${t.name}** (${t.kind}) — $cols"
      }
      s"### ${s.tables.size} table(s)\n\n${rows.mkString("\n")}"

  /** The `query` MCP tool description, generated from the live schema so it lists the tables that
    * actually exist (and their key columns) instead of a list that rots as migrations land. */
  def toolDescription(s: SchemaTables): String =
    val tables =
      if s.tables.isEmpty then "(schema unavailable — call the schema endpoint)"
      else
        s.tables
          .map { t =>
            val keys = t.columns.map(_.name).filter(Key.contains)
            val cols = (if keys.isEmpty then t.columns.map(_.name).take(6) else keys).mkString(", ")
            s"${t.name}($cols)"
          }
          .mkString("; ")
    "Run ONE read-only SQL statement against the Histmersion Postgres/PostGIS store and get columns + " +
      "rows back as a Markdown table. Use it for the questions `search`/`facets`/`histogram` cannot " +
      "express — cross-tabulations, counts by two dimensions, set differences. The statement runs " +
      "in a READ ONLY transaction with a statement timeout and a row cap, so INSERT/UPDATE/DELETE/" +
      "CREATE/DROP are refused by Postgres itself; SELECT only. Deep time is an int8range of SIGNED " +
      "years (BCE negative) — read the inclusive upper bound as `upper(period) - 1`. Geometry is " +
      "PostGIS; use ST_X/ST_Y/ST_AsText. Tables: " + tables + "."
