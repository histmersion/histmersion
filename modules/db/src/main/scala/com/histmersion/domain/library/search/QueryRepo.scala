package com.histmersion.domain.library.search

import com.histmersion.util.database.given

import com.histmersion.api.library.Query.*

import java.sql.{Connection, ResultSet, SQLException}
import com.augustnagro.magnum.DbCon

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
  *   3. `SET TRANSACTION READ ONLY` + `SET LOCAL statement_timeout` here, so `WITH x AS (DELETE …) SELECT …` — the
  *      classic blacklist escape — still fails on a database where layer 2 could not be provisioned. `maxRows` bounds
  *      the rows, `maxCellChars` bounds one cell.
  *
  * Written against plain JDBC rather than Magnum: the projection of an arbitrary statement is not known at compile
  * time, so there is no `DbCodec` to derive — the `ResultSetMetaData` is the schema.
  */
private[search] object QueryRepo:

  /** Run one statement. A rejected or failing statement is a VALUE (`Left(message)`), not an exception: a bad query is
    * an expected outcome of handing SQL to a model.
    */
  def run(
      statement: String,
      timeoutMs: Int,
      maxRows: Int,
      maxCellChars: Int
  )(using DbCon): Either[String, QueryResult] =
    com.histmersion.api.library.Query.check(statement).flatMap(text => exec(text, timeoutMs, maxRows, maxCellChars))

  /** On the caller's connection, in a read-only transaction of its own that is always rolled back (the connection's
    * auto-commit is restored afterwards).
    */
  private def exec(
      text: String,
      timeoutMs: Int,
      maxRows: Int,
      maxCellChars: Int
  )(using con: DbCon): Either[String, QueryResult] =
    val c: Connection = con.connection
    val autoCommit = c.getAutoCommit
    try
      c.setAutoCommit(false)
      c.setReadOnly(true)
      val st = c.createStatement()
      try
        // first statements of the transaction: the read-only mode and the timeout. Both take
        // integers from config, so neither can carry user text.
        val _readOnly = st.execute("SET TRANSACTION READ ONLY")
        val _timeout = st.execute(s"SET LOCAL statement_timeout = ${math.max(1, timeoutMs)}")
        val _ = (_readOnly, _timeout)
        st.setMaxRows(math.max(1, maxRows) + 1) // one extra row = "there was more"
        val started = System.nanoTime()
        val hasRs = st.execute(text)
        val elapsed = (System.nanoTime() - started) / 1000000L
        if !hasRs then
          // unreachable in a READ ONLY transaction (nothing that updates rows can run), but an
          // honest answer beats a NullPointerException if Postgres ever allows one.
          Right(QueryResult(Nil, Nil, 0, false, elapsed))
        else Right(read(st.getResultSet, maxRows, maxCellChars, elapsed))
      finally st.close()
    catch case e: SQLException => Left(message(e))
    finally
      try c.rollback()
      catch case _: SQLException => ()
      try
        c.setReadOnly(false)
        c.setAutoCommit(autoCommit)
      catch case _: SQLException => ()

  /** Postgres's own words, with the driver's stack noise stripped. */
  private def message(e: SQLException): String =
    Option(e.getMessage)
      .map(_.linesIterator.take(3).mkString(" ").trim)
      .filter(_.nonEmpty)
      .getOrElse(e.getClass.getSimpleName)

  private def read(rs: ResultSet, maxRows: Int, maxCellChars: Int, elapsedMs: Long): QueryResult =
    val md = rs.getMetaData
    val columns = (1 to md.getColumnCount).map(md.getColumnLabel).toList
    val rows = List.newBuilder[List[Option[String]]]
    var n = 0
    var more = false
    while rs.next() do
      if n >= maxRows then more = true
      else
        rows += columns.indices.map(i => cell(rs, i + 1, maxCellChars)).toList
        n += 1
    QueryResult(columns, rows.result(), n, more, elapsedMs)

  /** Every value is stringified — the only shape one static JSON schema can carry for an arbitrary projection. Long
    * values (an OCR body) are cut with an explicit marker rather than silently.
    */
  private def cell(rs: ResultSet, i: Int, maxCellChars: Int): Option[String] =
    val v = rs.getString(i)
    if v == null || rs.wasNull() then None
    else if v.length > maxCellChars then Some(v.take(maxCellChars) + s"… (+${v.length - maxCellChars} chars)")
    else Some(v)

  // ── the live schema ──────────────────────────────────────────────────────────────────────────

  /** `information_schema` for the public schema, tables and views alike. Read at request time, which is the point: the
    * `query` tool's description can then name the tables that exist TODAY.
    */
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
              name,
              if kind == "VIEW" then "view" else "table",
              cols.map((_, _, col, tpe, nullable) => SchemaColumn(col, tpe, nullable == "YES"))
            )
          }
      finally st.close()
