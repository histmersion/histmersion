package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

/** Read-only is enforced in three layers, because no single one of them holds:
  *
  *   1. [[check]]: exactly one top-level statement, not starting with a transaction- or session-control keyword. pgjdbc
  *      splits a `;`-separated batch and sends each part, so `SET TRANSACTION READ WRITE; DELETE …` would run both.
  *   2. a `histmersion_query` login role that is `NOSUPERUSER`, holds only `SELECT` and has
  *      `default_transaction_read_only = on`: the application role is typically a superuser, for which `pg_read_file()`
  *      and friends are open.
  *   3. `SET TRANSACTION READ ONLY` and `SET LOCAL statement_timeout` per statement, which still apply if the role
  *      could not be provisioned.
  */
object Query:
  type Markdown = Markdown.T
  object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchemaMarkdown = SchemaMarkdown.T
  object SchemaMarkdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Statement = Statement.T
  object Statement extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToolDescription = ToolDescription.T
  object ToolDescription extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type StatementsSql = String
  type MarkdownTableColumns = _root_.com.histmersion.api.library.Query.QueryResult.Columns
  val MarkdownTableColumns: _root_.com.histmersion.api.library.Query.QueryResult.Columns.type = _root_.com.histmersion.api.library.Query.QueryResult.Columns
  type MarkdownTableRows = _root_.com.histmersion.api.library.Query.QueryResult.Rows
  val MarkdownTableRows: _root_.com.histmersion.api.library.Query.QueryResult.Rows.type = _root_.com.histmersion.api.library.Query.QueryResult.Rows

  final case class QueryRequest(sql: QueryRequest.Sql) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema



  final case class QueryResult(
      columns: List[QueryResult.Columns],
      rows: List[List[Option[QueryResult.Rows]]],
      rowCount: QueryResult.RowCount,
      truncated: QueryResult.Truncated,
      elapsedMs: QueryResult.ElapsedMs
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object QueryResult:
    type Columns = Columns.T
    object Columns extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rows = Rows.T
    object Rows extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RowCount = RowCount.T
    object RowCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Truncated = Truncated.T
    object Truncated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ElapsedMs = ElapsedMs.T
    object ElapsedMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class SchemaColumn(name: SchemaColumn.Name, dataType: SchemaColumn.DataType, nullable: SchemaColumn.Nullable)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object SchemaColumn:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DataType = DataType.T
    object DataType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Nullable = Nullable.T
    object Nullable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class SchemaTable(name: SchemaTable.Name, kind: SchemaTable.Kind, columns: List[SchemaColumn])
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object SchemaTable:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class SchemaTables(tables: List[SchemaTable]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  private val Forbidden = Set(
    "SET",
    "RESET",
    "BEGIN",
    "START",
    "COMMIT",
    "ROLLBACK",
    "ABORT",
    "END",
    "SAVEPOINT",
    "RELEASE",
    "DISCARD",
    "DO",
    "CALL"
  )

  /** Honours everything that may hold a `;` in Postgres: `'…'` strings (with `''` escapes), `"…"` identifiers,
    * `$tag$…$tag$` dollar quoting, `--` line comments and block comments, which Postgres nests.
    */
  def statements(sql: com.histmersion.api.library.Query.StatementsSql): List[com.histmersion.api.library.Query.Statement] =
    val out = List.newBuilder[String]
    val cur = StringBuilder()
    var i = 0
    val n = (sql).length
    var depth = 0
    def at(k: Int): Char = if k >= 0 && k < n then ((sql).charAt((k).value)) else (0.toChar)
    while i < n do
      val c = (sql).charAt(i)
      if depth > 0 then
        if c == '*' && at((i + 1)) == ('/') then { depth -= 1; cur.append("*/"); i += 2 }
        else if c == '/' && at((i + 1)) == ('*') then { depth += 1; cur.append("/*"); i += 2 }
        else { cur.append(c); i += 1 }
      else if c == '-' && at((i + 1)) == ('-') then
        while i < n && (sql).value.charAt(i) != '\n' do { cur.append((sql).charAt(i)); i += 1 }
      else if c == '/' && at((i + 1)) == ('*') then { depth += 1; cur.append("/*"); i += 2 }
      else if c == '\'' || c == '"' then
        cur.append(c); i += 1
        var closed = false
        while i < n && !closed do
          val q = (sql).charAt(i)
          if q == c && at((i + 1)) == (c) then { cur.append(c).append(c); i += 2 }
          else if q == c then { cur.append(c); i += 1; closed = true }
          else { cur.append(q); i += 1 }
      else if c == '$' then
        dollarTag(sql, (i)) match
          case Some(tag) =>
            val end = (sql).indexOf(tag, i + (tag).value.length)
            val to = if end < 0 then n else end + (tag).value.length
            cur.append((sql).substring(i, to)); i = to
          case None => { cur.append(c); i += 1 }
      else if c == ';' then { out += cur.toString; cur.clear(); i += 1 }
      else { cur.append(c); i += 1 }
    out += cur.toString
    (out.result().map(_.trim).filter(_.nonEmpty)).map(refactorValue => _root_.com.histmersion.api.library.Query.Statement(refactorValue))

  private def dollarTag(sql: String, i: Int): Option[String] =
    val close = (sql).indexOf('$', (i).value + 1)
    if close < 0 then None
    else
      val tag = (sql).substring((i).value + 1, close)
      if tag.isEmpty || (tag.head.isLetter || tag.head == '_') && tag.forall(ch => ch.isLetterOrDigit || ch == '_')
      then Some(((sql).substring((i).value, close + 1)))
      else None

  def leadingKeyword(statement: com.histmersion.api.library.Query.Statement): String =
    def afterBlockComment(text: String): String =
      var at = 2
      var depth = 1
      while at < (text).length && depth > 0 do
        if (text).startsWith("/*", at) then
          depth += 1
          at += 2
        else if (text).startsWith("*/", at) then
          depth -= 1
          at += 2
        else at += 1
      if depth == 0 then ((text).substring(at).trim) else ("")
    var s = (statement).value.trim
    var go = true
    while go do
      if s.startsWith("--") then s = s.dropWhile(_ != '\n').trim
      else if s.startsWith("/*") then s = (afterBlockComment((s)))
      else go = false
    (s.takeWhile(c => c.isLetter).toUpperCase)

  def check(sql: com.histmersion.api.library.Query.Statement): Either[com.histmersion.api.library.Query.Problem, com.histmersion.api.library.Query.Statement] =
    val text = (sql).value.trim
    if text.isEmpty then Left(_root_.com.histmersion.api.library.Query.Problem("empty statement"))
    else
      statements(_root_.com.histmersion.api.library.Query.StatementsSql(text)) match
        case Nil        => Left(_root_.com.histmersion.api.library.Query.Problem("empty statement"))
        case one :: Nil =>
          val kw = leadingKeyword(_root_.com.histmersion.api.library.Query.Statement((one).value))
          if Forbidden.contains((kw)) then
            Left(
              _root_.com.histmersion.api.library.Query.Problem(s"'$kw' is not allowed here — `query` runs ONE read-only statement (SELECT, or a " +
                "WITH that only reads); transaction and session control are refused")
            )
          else Right(_root_.com.histmersion.api.library.Query.Statement((one).value))
        case many =>
          Left(
            _root_.com.histmersion.api.library.Query.Problem(s"${many.size} statements were given — `query` runs exactly ONE; " +
              "drop the ';'-separated batch and ask again")
          )

  private def cell(v: Option[String]): String =
    v.getOrElse("NULL").replace("|", "\\|").linesIterator.mkString(" ").trim

  def markdownTable(columns: List[com.histmersion.api.library.Query.MarkdownTableColumns], rows: List[List[Option[com.histmersion.api.library.Query.MarkdownTableRows]]]): String =
    if columns.isEmpty then ("_(no columns)_")
    else
      val header = columns.map((_).value.replace("|", "\\|")).mkString("| ", " | ", " |")
      val rule = columns.map(_ => "---").mkString("|", "|", "|")
      val body = rows.map(r => columns.indices.map(i => cell(r.lift(i).flatten)).mkString("| ", " | ", " |"))
      // No stripMargin: every row starts with '|', which stripMargin would eat.
      ((List(header, rule) ++ body).mkString("\n"))

  def markdown(r: QueryResult): com.histmersion.api.library.Query.Markdown =
    val head =
      s"### ${r.rowCount} row(s)${if ((r.truncated) == _root_.com.histmersion.api.library.Query.QueryResult.Truncated(true)) then " (truncated by the row cap)" else ""} · ${r.elapsedMs} ms"
    if r.rows.isEmpty then _root_.com.histmersion.api.library.Query.Markdown(s"$head\n\n_no rows_")
    else _root_.com.histmersion.api.library.Query.Markdown(s"$head\n\n${markdownTable(r.columns, r.rows)}")

  private val Key = Set(
    "id",
    "citekey",
    "src",
    "dst",
    "kind",
    "list",
    "status",
    "title",
    "doi",
    "year",
    "topics",
    "regions",
    "tags",
    "period",
    "claim",
    "region_name",
    "geom",
    "ord",
    "topic",
    "slug",
    "created_at",
    "done_at",
    "text",
    "name",
    "label"
  )

  def schemaMarkdown(s: SchemaTables): com.histmersion.api.library.Query.SchemaMarkdown =
    if s.tables.isEmpty then _root_.com.histmersion.api.library.Query.SchemaMarkdown("_(no tables)_")
    else
      val rows = s.tables.map { t =>
        val cols = t.columns.map(c => s"${c.name} ${c.dataType}").mkString(", ")
        s"- **${t.name}** (${t.kind}) — $cols"
      }
      _root_.com.histmersion.api.library.Query.SchemaMarkdown(s"### ${s.tables.size} table(s)\n\n${rows.mkString("\n")}")

  def toolDescription(s: SchemaTables): com.histmersion.api.library.Query.ToolDescription =
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
    _root_.com.histmersion.api.library.Query.ToolDescription("Run ONE read-only SQL statement against the Histmersion Postgres/PostGIS store and get columns + " +
      "rows back as a Markdown table. Use it for the questions `search`/`facets`/`histogram` cannot " +
      "express — cross-tabulations, counts by two dimensions, set differences. The statement runs " +
      "in a READ ONLY transaction with a statement timeout and a row cap, so INSERT/UPDATE/DELETE/" +
      "CREATE/DROP are refused by Postgres itself; SELECT only. Deep time is an int8range of SIGNED " +
      "years (BCE negative) — read the inclusive upper bound as `upper(period) - 1`. Geometry is " +
      "PostGIS; use ST_X/ST_Y/ST_AsText. Tables: " + tables + ".")
