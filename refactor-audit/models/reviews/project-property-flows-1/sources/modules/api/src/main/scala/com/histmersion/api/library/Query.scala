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

  type StatementsAtResult = StatementsAtResult.T
  object StatementsAtResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatementsAtK = StatementsAtK.T
  object StatementsAtK extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadingKeywordAfterBlockCommentResult = LeadingKeywordAfterBlockCommentResult.T
  object LeadingKeywordAfterBlockCommentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadingKeywordAfterBlockCommentText = LeadingKeywordAfterBlockCommentText.T
  object LeadingKeywordAfterBlockCommentText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type StatementsResult = StatementsResult.T
  object StatementsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StatementsSql = _root_.com.histmersion.api.library.Query.DollarTagSql
  val StatementsSql: _root_.com.histmersion.api.library.Query.DollarTagSql.type = _root_.com.histmersion.api.library.Query.DollarTagSql
  type DollarTagResult = DollarTagResult.T
  object DollarTagResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DollarTagSql = DollarTagSql.T
  object DollarTagSql extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DollarTagI = DollarTagI.T
  object DollarTagI extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadingKeywordResult = LeadingKeywordResult.T
  object LeadingKeywordResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeadingKeywordStatement = LeadingKeywordStatement.T
  object LeadingKeywordStatement extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart1 = CheckResultPart1.T
  object CheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart2 = CheckResultPart2.T
  object CheckResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckSql = CheckSql.T
  object CheckSql extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CellResult = CellResult.T
  object CellResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CellV = CellV.T
  object CellV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownTableResult = MarkdownTableResult.T
  object MarkdownTableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownTableColumns = _root_.com.histmersion.api.library.Query.QueryResult.Columns
  val MarkdownTableColumns: _root_.com.histmersion.api.library.Query.QueryResult.Columns.type = _root_.com.histmersion.api.library.Query.QueryResult.Columns
  type MarkdownTableRows = _root_.com.histmersion.api.library.Query.QueryResult.Rows
  val MarkdownTableRows: _root_.com.histmersion.api.library.Query.QueryResult.Rows.type = _root_.com.histmersion.api.library.Query.QueryResult.Rows
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SchemaMarkdownResult = SchemaMarkdownResult.T
  object SchemaMarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ToolDescriptionResult = ToolDescriptionResult.T
  object ToolDescriptionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class QueryRequest(sql: QueryRequest.Sql) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object QueryRequest:
    type Sql = Sql.T
    object Sql extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


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
  def statements(sql: com.histmersion.api.library.Query.StatementsSql): List[com.histmersion.api.library.Query.StatementsResult] =
    val out = List.newBuilder[String]
    val cur = StringBuilder()
    var i = 0
    val n = (sql).value.length
    var depth = 0
    def at(k: com.histmersion.api.library.Query.StatementsAtK): com.histmersion.api.library.Query.StatementsAtResult = if k >= 0 && k < n then _root_.com.histmersion.api.library.Query.StatementsAtResult((sql).value.charAt((k).value)) else _root_.com.histmersion.api.library.Query.StatementsAtResult(0.toChar)
    while i < n do
      val c = (sql).value.charAt(i)
      if depth > 0 then
        if c == '*' && at(_root_.com.histmersion.api.library.Query.StatementsAtK(i + 1)) == _root_.com.histmersion.api.library.Query.StatementsAtResult('/') then { depth -= 1; cur.append("*/"); i += 2 }
        else if c == '/' && at(_root_.com.histmersion.api.library.Query.StatementsAtK(i + 1)) == _root_.com.histmersion.api.library.Query.StatementsAtResult('*') then { depth += 1; cur.append("/*"); i += 2 }
        else { cur.append(c); i += 1 }
      else if c == '-' && at(_root_.com.histmersion.api.library.Query.StatementsAtK(i + 1)) == _root_.com.histmersion.api.library.Query.StatementsAtResult('-') then
        while i < n && (sql).value.charAt(i) != '\n' do { cur.append((sql).value.charAt(i)); i += 1 }
      else if c == '/' && at(_root_.com.histmersion.api.library.Query.StatementsAtK(i + 1)) == _root_.com.histmersion.api.library.Query.StatementsAtResult('*') then { depth += 1; cur.append("/*"); i += 2 }
      else if c == '\'' || c == '"' then
        cur.append(c); i += 1
        var closed = false
        while i < n && !closed do
          val q = (sql).value.charAt(i)
          if q == c && at(_root_.com.histmersion.api.library.Query.StatementsAtK(i + 1)) == _root_.com.histmersion.api.library.Query.StatementsAtResult(c) then { cur.append(c).append(c); i += 2 }
          else if q == c then { cur.append(c); i += 1; closed = true }
          else { cur.append(q); i += 1 }
      else if c == '$' then
        dollarTag(sql, _root_.com.histmersion.api.library.Query.DollarTagI(i)) match
          case Some(tag) =>
            val end = (sql).value.indexOf(tag, i + (tag).value.length)
            val to = if end < 0 then n else end + tag.length
            cur.append((sql).value.substring(i, to)); i = to
          case None => { cur.append(c); i += 1 }
      else if c == ';' then { out += cur.toString; cur.clear(); i += 1 }
      else { cur.append(c); i += 1 }
    out += cur.toString
    (out.result().map(_.trim).filter(_.nonEmpty)).map(refactorValue => _root_.com.histmersion.api.library.Query.StatementsResult(refactorValue))

  private def dollarTag(sql: com.histmersion.api.library.Query.DollarTagSql, i: com.histmersion.api.library.Query.DollarTagI): Option[com.histmersion.api.library.Query.DollarTagResult] =
    val close = (sql).value.indexOf('$', (i).value + 1)
    if close < 0 then None
    else
      val tag = (sql).value.substring((i).value + 1, close)
      if tag.isEmpty || (tag.head.isLetter || tag.head == '_') && tag.forall(ch => ch.isLetterOrDigit || ch == '_')
      then Some(_root_.com.histmersion.api.library.Query.DollarTagResult((sql).value.substring((i).value, close + 1)))
      else None

  def leadingKeyword(statement: com.histmersion.api.library.Query.LeadingKeywordStatement): com.histmersion.api.library.Query.LeadingKeywordResult =
    def afterBlockComment(text: com.histmersion.api.library.Query.LeadingKeywordAfterBlockCommentText): com.histmersion.api.library.Query.LeadingKeywordAfterBlockCommentResult =
      var at = 2
      var depth = 1
      while at < (text).value.length && depth > 0 do
        if (text).value.startsWith("/*", at) then
          depth += 1
          at += 2
        else if (text).value.startsWith("*/", at) then
          depth -= 1
          at += 2
        else at += 1
      if depth == 0 then _root_.com.histmersion.api.library.Query.LeadingKeywordAfterBlockCommentResult((text).value.substring(at).trim) else _root_.com.histmersion.api.library.Query.LeadingKeywordAfterBlockCommentResult("")
    var s = (statement).value.trim
    var go = true
    while go do
      if s.startsWith("--") then s = s.dropWhile(_ != '\n').trim
      else if s.startsWith("/*") then s = (afterBlockComment(_root_.com.histmersion.api.library.Query.LeadingKeywordAfterBlockCommentText(s))).value
      else go = false
    _root_.com.histmersion.api.library.Query.LeadingKeywordResult(s.takeWhile(c => c.isLetter).toUpperCase)

  def check(sql: com.histmersion.api.library.Query.CheckSql): Either[com.histmersion.api.library.Query.CheckResultPart1, com.histmersion.api.library.Query.CheckResultPart2] =
    val text = (sql).value.trim
    if text.isEmpty then Left(_root_.com.histmersion.api.library.Query.CheckResultPart1("empty statement"))
    else
      statements(_root_.com.histmersion.api.library.Query.StatementsSql(text)) match
        case Nil        => Left(_root_.com.histmersion.api.library.Query.CheckResultPart1("empty statement"))
        case one :: Nil =>
          val kw = leadingKeyword(one)
          if Forbidden.contains(kw) then
            Left(
              _root_.com.histmersion.api.library.Query.CheckResultPart1(s"'$kw' is not allowed here — `query` runs ONE read-only statement (SELECT, or a " +
                "WITH that only reads); transaction and session control are refused")
            )
          else Right(one)
        case many =>
          Left(
            _root_.com.histmersion.api.library.Query.CheckResultPart1(s"${many.size} statements were given — `query` runs exactly ONE; " +
              "drop the ';'-separated batch and ask again")
          )

  private def cell(v: Option[com.histmersion.api.library.Query.CellV]): com.histmersion.api.library.Query.CellResult =
    v.getOrElse("NULL").replace("|", "\\|").linesIterator.mkString(" ").trim

  def markdownTable(columns: List[com.histmersion.api.library.Query.MarkdownTableColumns], rows: List[List[Option[com.histmersion.api.library.Query.MarkdownTableRows]]]): com.histmersion.api.library.Query.MarkdownTableResult =
    if columns.isEmpty then _root_.com.histmersion.api.library.Query.MarkdownTableResult("_(no columns)_")
    else
      val header = columns.map((_).value.replace("|", "\\|")).mkString("| ", " | ", " |")
      val rule = columns.map(_ => "---").mkString("|", "|", "|")
      val body = rows.map(r => columns.indices.map(i => cell(r.lift(i).flatten)).mkString("| ", " | ", " |"))
      // No stripMargin: every row starts with '|', which stripMargin would eat.
      _root_.com.histmersion.api.library.Query.MarkdownTableResult((List(header, rule) ++ body).mkString("\n"))

  def markdown(r: QueryResult): com.histmersion.api.library.Query.MarkdownResult =
    val head =
      s"### ${r.rowCount} row(s)${if ((r.truncated) == _root_.com.histmersion.api.library.Query.QueryResult.Truncated(true)) then " (truncated by the row cap)" else ""} · ${r.elapsedMs} ms"
    if r.rows.isEmpty then _root_.com.histmersion.api.library.Query.MarkdownResult(s"$head\n\n_no rows_")
    else s"$head\n\n${markdownTable(r.columns, r.rows)}"

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

  def schemaMarkdown(s: SchemaTables): com.histmersion.api.library.Query.SchemaMarkdownResult =
    if s.tables.isEmpty then _root_.com.histmersion.api.library.Query.SchemaMarkdownResult("_(no tables)_")
    else
      val rows = s.tables.map { t =>
        val cols = t.columns.map(c => s"${c.name} ${c.dataType}").mkString(", ")
        s"- **${t.name}** (${t.kind}) — $cols"
      }
      _root_.com.histmersion.api.library.Query.SchemaMarkdownResult(s"### ${s.tables.size} table(s)\n\n${rows.mkString("\n")}")

  def toolDescription(s: SchemaTables): com.histmersion.api.library.Query.ToolDescriptionResult =
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
    _root_.com.histmersion.api.library.Query.ToolDescriptionResult("Run ONE read-only SQL statement against the Histmersion Postgres/PostGIS store and get columns + " +
      "rows back as a Markdown table. Use it for the questions `search`/`facets`/`histogram` cannot " +
      "express — cross-tabulations, counts by two dimensions, set differences. The statement runs " +
      "in a READ ONLY transaction with a statement timeout and a row cap, so INSERT/UPDATE/DELETE/" +
      "CREATE/DROP are refused by Postgres itself; SELECT only. Deep time is an int8range of SIGNED " +
      "years (BCE negative) — read the inclusive upper bound as `upper(period) - 1`. Geometry is " +
      "PostGIS; use ST_X/ST_Y/ST_AsText. Tables: " + tables + ".")
