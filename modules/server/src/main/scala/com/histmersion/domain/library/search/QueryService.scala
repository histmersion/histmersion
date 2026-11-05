package com.histmersion.domain.library.search

import com.histmersion.api.library.Query.{QueryResult, SchemaTable}

import com.histmersion.util.database.Transactor

import javax.sql.DataSource

/** The read-only SQL escape hatch and the live schema it reads. A statement runs on the read-only role's pool
  * (`queries`) in a read-only transaction of its own, with a timeout and caps; the schema is read on the application's
  * pool (`schema`). A mechanism over JDBC, so it has no in-memory twin.
  */
trait QueryService:
  def run(statement: String, timeoutMs: Int, maxRows: Int, maxCellChars: Int): Either[String, QueryResult]
  def tables: List[SchemaTable]

object QueryService:
  def apply(queries: DataSource, schema: DataSource)(using timer: com.histmersion.util.time.Timer): QueryService =
    Implementation(Transactor(queries), Transactor(schema))

  final private class Implementation(queries: Transactor, schema: Transactor)(using timer: com.histmersion.util.time.Timer) extends QueryService:
    def run(statement: String, timeoutMs: Int, maxRows: Int, maxCellChars: Int): Either[String, QueryResult] =
      try queries.readOnlyRollback(timeoutMs)(QueryRepo.run(statement, maxRows, maxCellChars))
      catch case error: java.sql.SQLException => Left(QueryRepo.message(error))
    def tables: List[SchemaTable] = schema.read(QueryRepo.tables)
