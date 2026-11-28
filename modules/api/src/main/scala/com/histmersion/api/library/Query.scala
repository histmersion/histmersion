package com.histmersion
package api.library

import com.histmersion.util.problem.Problem

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.json.TwoStates

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
  object Markdown extends Newtype[String, Pure]
  type SchemaMarkdown = SchemaMarkdown.T
  @tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
  object SchemaMarkdown extends Newtype[String, Pure]
  type Statement = Statement.T
  object Statement extends Newtype[String, Pure]
  type ToolDescription = ToolDescription.T
  object ToolDescription extends Newtype[String, Pure]

  type StatementsSql = String
  export QueryResult.{Columns as MarkdownTableColumns, Rows as MarkdownTableRows}

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class QueryRequest(sql: QueryRequest.Sql) derives JsonCodec, KindlingsSchema, api.json.Schema

  object QueryRequest:
    export Query.Statement as Sql

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class QueryResult(
      columns: List[QueryResult.Columns],
      rows: List[List[Option[QueryResult.Rows]]],
      rowCount: QueryResult.RowCount,
      truncated: QueryResult.Truncated,
      elapsedMs: QueryResult.ElapsedMs
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object QueryResult:
    type Columns = Columns.T
    object Columns extends Newtype[String, Pure]
    type Rows = Rows.T
    object Rows extends Newtype[String, Pure]
    type RowCount = RowCount.T
    object RowCount extends Newtype[Int, Pure]
    enum Truncated derives CanEqual:
      case Truncated, Complete
    object Truncated extends TwoStates[Truncated]:
      extension (truncated: Truncated) def truncated: Boolean = truncated == Truncated
    type ElapsedMs = ElapsedMs.T
    object ElapsedMs extends Newtype[Long, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class SchemaColumn(
      name: SchemaColumn.Name,
      dataType: SchemaColumn.DataType,
      nullable: SchemaColumn.Nullable
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object SchemaColumn:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type DataType = DataType.T
    object DataType extends Newtype[String, Pure]
    enum Nullable derives CanEqual:
      case Nullable, Required
    object Nullable extends TwoStates[Nullable]:
      extension (nullable: Nullable) def nullable: Boolean = nullable == Nullable

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class SchemaTable(name: SchemaTable.Name, kind: SchemaTable.Kind, columns: List[SchemaColumn])
      derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  object SchemaTable:
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  final case class SchemaTables(tables: List[SchemaTable]) derives JsonCodec, KindlingsSchema, api.json.Schema

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
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def statements(sql: StatementsSql): List[Statement] =
    val out = List.newBuilder[String]
    val cur = StringBuilder()
    var i = 0
    val n = (sql).length
    var depth = 0
    def at(k: Int): Char = if k >= 0 && k < n then ((sql).charAt((k))) else (0.toChar)
    while i < n do
      val c = (sql).charAt(i)
      if depth > 0 then
        if c == '*' && at((i + 1)) == ('/') then { depth -= 1; cur.append("*/"); i += 2 }
        else if c == '/' && at((i + 1)) == ('*') then { depth += 1; cur.append("/*"); i += 2 }
        else { cur.append(c); i += 1 }
      else if c == '-' && at((i + 1)) == ('-') then
        while i < n && (sql).charAt(i) != '\n' do { cur.append((sql).charAt(i)); i += 1 }
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
            val end = (sql).indexOf(tag, i + (tag).length)
            val to = if end < 0 then n else end + (tag).length
            cur.append((sql).substring(i, to)); i = to
          case None => { cur.append(c); i += 1 }
      else if c == ';' then { out += cur.toString; cur.clear(); i += 1 }
      else { cur.append(c); i += 1 }
    out += cur.toString
    (out.result().map(_.trim).filter(_.nonEmpty)).map(refactorValue => Statement(refactorValue))

  private def dollarTag(sql: String, i: Int): Option[String] =
    val close = (sql).indexOf('$', (i) + 1)
    if close < 0 then None
    else
      val tag = (sql).substring((i) + 1, close)
      if tag.isEmpty || (tag.head.isLetter || tag.head == '_') && tag.forall(ch => ch.isLetterOrDigit || ch == '_')
      then Some(((sql).substring((i), close + 1)))
      else None

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def leadingKeyword(statement: Statement): String =
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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def check(sql: Statement): Either[Problem, Statement] =
    val text = (sql).value.trim
    if text.isEmpty then Left(Problem.Invalid.Missing.here(Problem.Field("sql")))
    else
      statements(text) match
        case Nil        => Left(Problem.Invalid.Missing.here(Problem.Field("sql")))
        case one :: Nil =>
          val kw = leadingKeyword(one)
          if Forbidden.contains((kw)) then
            Left(
              Problem.Invalid.Unknown.here(
                Problem.TypeName.of(Statement),
                Problem.Raw(s"$kw"),
                Vector(Problem.Raw("SELECT"), Problem.Raw("WITH"))
              )
            )
          else Right(one)
        case _ =>
          Left(Problem.Refused.Policy(Problem.Refused.Rule.OneStatement))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  private def cell(v: Option[Query.MarkdownTableRows]): String =
    v.map(_.value).getOrElse("NULL").replace("|", "\\|").linesIterator.mkString(" ").trim

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  def markdownTable(
      columns: List[Query.MarkdownTableColumns],
      rows: List[List[Option[Query.MarkdownTableRows]]]
  ): String =
    if columns.isEmpty then ("_(no columns)_")
    else
      val header = columns.map((_).value.replace("|", "\\|")).mkString("| ", " | ", " |")
      val rule = columns.map(_ => "---").mkString("|", "|", "|")
      val body = rows.map(r => columns.indices.map(i => cell(r.lift(i).flatten)).mkString("| ", " | ", " |"))
      // No stripMargin: every row starts with '|', which stripMargin would eat.
      ((List(header, rule) ++ body).mkString("\n"))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdown(r: QueryResult): Markdown =
    val head =
      s"### ${r.rowCount} row(s)${
          if (r.truncated) == QueryResult.Truncated.Truncated then " (truncated by the row cap)" else ""
        } · ${r.elapsedMs} ms"
    if r.rows.isEmpty then Markdown(s"$head\n\n_no rows_")
    else Markdown(s"$head\n\n${markdownTable(r.columns, r.rows)}")

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

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def schemaMarkdown(s: SchemaTables): SchemaMarkdown =
    if s.tables.isEmpty then SchemaMarkdown("_(no tables)_")
    else
      val rows = s.tables.map { t =>
        val cols = t.columns.map(c => s"${c.name} ${c.dataType}").mkString(", ")
        s"- **${t.name}** (${t.kind}) — $cols"
      }
      SchemaMarkdown(s"### ${s.tables.size} table(s)\n\n${rows.mkString("\n")}")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def toolDescription(s: SchemaTables): ToolDescription =
    val tables =
      if s.tables.isEmpty then "(schema unavailable — call the schema endpoint)"
      else
        s.tables
          .map { t =>
            val keys = t.columns.map(_.name).filter(n => Key.contains(n.value))
            val cols = (if keys.isEmpty then t.columns.map(_.name).take(6) else keys).mkString(", ")
            s"${t.name}($cols)"
          }
          .mkString("; ")
    ToolDescription(
      "Run ONE read-only SQL statement against the Histmersion Postgres/PostGIS store and get columns + " +
        "rows back as a Markdown table. Use it for the questions `search`/`facets`/`histogram` cannot " +
        "express — cross-tabulations, counts by two dimensions, set differences. The statement runs " +
        "in a READ ONLY transaction with a statement timeout and a row cap, so INSERT/UPDATE/DELETE/" +
        "CREATE/DROP are refused by Postgres itself; SELECT only. Deep time is an int8range of SIGNED " +
        "years (BCE negative) — read the inclusive upper bound as `upper(period) - 1`. Geometry is " +
        "PostGIS; use ST_X/ST_Y/ST_AsText. Tables: " + tables + "."
    )
