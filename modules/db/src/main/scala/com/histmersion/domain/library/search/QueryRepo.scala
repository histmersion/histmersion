package com.histmersion
package domain.library.search
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.util.database.given

import com.histmersion.api.library.Query.*

import java.sql.{Connection, ResultSet, SQLException}
import com.augustnagro.magnum.DbCon
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.time.Timer
import com.histmersion.api.library.Query
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The read-only SQL escape hatch (`POST /api/query`, `histmersion query`, MCP `query`; plan §5.2 2.8), plus the live
  * `information_schema` shape the tool description is generated from.
  *
  * **Three layers, none of which is sufficient alone** (the full argument is on [[com.histmersion.api.library.Query]]):
  *
  *   1. [[com.histmersion.api.library.Query.check]] — ONE top-level statement, and not a session/transaction-control
  *      one. Without it, pgjdbc runs a `;`-batch and `SET TRANSACTION READ WRITE; DELETE …` beat the guard below by
  *      turning it off first.
  *   2. the caller's connection comes from the DataSource of the dedicated **`histmersion_query`** role (migration
  *      V011) — SELECT and nothing else, `default_transaction_read_only = on` baked in. This is what actually stops a
  *      superuser-only `pg_read_file()`; the transaction settings never could.
  *   3. `SET TRANSACTION READ ONLY` + `SET LOCAL statement_timeout` in the caller's Transactor scope, so
  *      `WITH x AS (DELETE …) SELECT …` — the classic blacklist escape — still fails on a database where layer 2 could
  *      not be provisioned. `maxRows` bounds the rows, `maxCellChars` bounds one cell.
  *
  * Written against plain JDBC rather than Magnum: the projection of an arbitrary statement is not known at compile
  * time, so there is no `DbCodec` to derive — the `ResultSetMetaData` is the schema.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@atBoundary(BoundaryKind.Sql, "reads and writes the query tables and their row codecs")
private[search] object QueryRepo:
  type MaxCellChars = MaxCellChars.T
  object MaxCellChars extends Newtype[Int, Pure]
  type MaxRows = MaxRows.T
  object MaxRows extends Newtype[Int, Pure]
  type Statement = Statement.T
  object Statement extends Newtype[String, Pure]

  /** Run one statement. A rejected or failing statement is a VALUE (`Left(message)`), not an exception: a bad query is
    * an expected outcome of handing SQL to a model.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def run(statement: Statement, maxRows: MaxRows, maxCellChars: MaxCellChars)(using
      con: DbCon,
      timer: Timer
  ): Either[Problem, QueryResult] =
    check(Query.Statement(statement.value))
      .flatMap(text => exec(Statement(text.value), maxRows, maxCellChars))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def exec(text: Statement, maxRows: MaxRows, maxCellChars: MaxCellChars)(using
      con: DbCon,
      timer: Timer
  ): Either[Problem, QueryResult] =
    val c: Connection = con.connection
    try
      val st = c.createStatement()
      try
        st.setMaxRows(math.max(1, maxRows.value) + 1)
        val started = timer.start()
        val hasRs = st.execute(text.value)
        val elapsed = started.elapsed.toMillis
        if !hasRs then
          // unreachable in a READ ONLY transaction (nothing that updates rows can run), but an
          // honest answer beats a NullPointerException if Postgres ever allows one.
          Right(
            QueryResult(Nil, Nil, QueryResult.RowCount(0), QueryResult.Truncated(false), QueryResult.ElapsedMs(elapsed))
          )
        else Right(read(st.getResultSet, maxRows, maxCellChars, QueryResult.ElapsedMs(elapsed)))
      finally st.close()
    catch case e: SQLException => Left(message(e))

  /** Postgres's own words, with the driver's stack noise stripped. */
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def message(e: SQLException): Problem =
    Problem.Unclassified(
      Legacy.Text(
        Option(e.getMessage)
          .map(_.linesIterator.take(3).mkString(" ").trim)
          .filter(_.nonEmpty)
          .getOrElse(e.getClass.getSimpleName)
      )
    )

  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def read(
      rs: ResultSet,
      maxRows: MaxRows,
      maxCellChars: MaxCellChars,
      elapsedMs: QueryResult.ElapsedMs
  ): QueryResult =
    val md = rs.getMetaData
    val columns = (1 to md.getColumnCount).map(md.getColumnLabel).toList
    val rows = List.newBuilder[List[Option[QueryResult.Rows]]]
    var n = 0
    var more = false
    while rs.next() do
      if n >= maxRows.value then more = true
      else
        rows += columns.indices.map(i => cell(rs, i + 1, maxCellChars)).toList
        n += 1
    QueryResult(
      columns.map(QueryResult.Columns(_)),
      rows.result(),
      QueryResult.RowCount(n),
      QueryResult.Truncated(more),
      elapsedMs
    )

  /** Every value is stringified — the only shape one static JSON schema can carry for an arbitrary projection. Long
    * values (an OCR body) are cut with an explicit marker rather than silently.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  private def cell(rs: ResultSet, i: Int, maxCellChars: MaxCellChars): Option[QueryResult.Rows] =
    val v = rs.getString(i)
    if v == null || rs.wasNull() then None
    else if v.length > maxCellChars.value then
      Some(QueryResult.Rows(v.take(maxCellChars.value) + s"… (+${v.length - maxCellChars.value} chars)"))
    else Some(QueryResult.Rows(v))

  // ── the live schema ──────────────────────────────────────────────────────────────────────────

  /** `information_schema` for the public schema, tables and views alike. Read at request time, which is the point: the
    * `query` tool's description can then name the tables that exist TODAY.
    */
  @tolerated(LintRule.TotalOrder, "information_schema of one schema: a table's name and a column's position are a key")
  def tables(using con: DbCon): List[SchemaTable] =
    val c = con.connection
    locally:
      val st = c.createStatement()
      try
        val rs = st.executeQuery("""SELECT t.table_name, t.table_type, c.column_name, c.data_type, c.is_nullable
             FROM information_schema.tables t
             JOIN information_schema.columns c
               ON c.table_schema = t.table_schema AND c.table_name = t.table_name
             WHERE t.table_schema = 'public'
               AND t.table_name NOT IN ('spatial_ref_sys', 'geometry_columns', 'geography_columns',
                                        'flyway_schema_history')
             ORDER BY t.table_name, c.ordinal_position""")
        val rows = Iterator
          .continually(rs)
          .takeWhile(_.next())
          .map(r => (r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)))
          .toList
        rows
          .groupBy((name, kind, _, _, _) => (name, kind))
          .toList
          .sortBy((k, _) => k._1)
          .map { case ((name, kind), cols) =>
            SchemaTable(
              SchemaTable.Name(name),
              if kind == "VIEW" then SchemaTable.Kind("view") else SchemaTable.Kind("table"),
              cols.map((_, _, col, tpe, nullable) =>
                SchemaColumn(
                  SchemaColumn.Name(col),
                  SchemaColumn.DataType(tpe),
                  SchemaColumn.Nullable(nullable == "YES")
                )
              )
            )
          }
      finally st.close()
