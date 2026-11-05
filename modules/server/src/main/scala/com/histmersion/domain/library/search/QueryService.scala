package com.histmersion.domain.library.search

import com.histmersion.api.library.Query.{QueryResult, SchemaTable}

import com.histmersion.util.database.Transactor

import javax.sql.DataSource

/** The read-only SQL escape hatch and the live schema it reads. A statement runs on the read-only role's pool
  * (`queries`) in a read-only transaction of its own, with a timeout and caps; the schema is read on the application's
  * pool (`schema`). A mechanism over JDBC, so it has no in-memory twin.
  */
trait QueryService:
  def run(statement: com.histmersion.domain.library.search.QueryService.RunStatement, timeoutMs: com.histmersion.domain.library.search.QueryService.RunTimeoutMs, maxRows: com.histmersion.domain.library.search.QueryService.RunMaxRows, maxCellChars: com.histmersion.domain.library.search.QueryService.RunMaxCellChars): Either[com.histmersion.domain.library.search.QueryService.RunResultPart1, QueryResult]
  def tables: List[SchemaTable]

object QueryService:
  type RunResultPart1 = RunResultPart1.T
  object RunResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunStatement = RunStatement.T
  object RunStatement extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunTimeoutMs = RunTimeoutMs.T
  object RunTimeoutMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunMaxRows = RunMaxRows.T
  object RunMaxRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunMaxCellChars = RunMaxCellChars.T
  object RunMaxCellChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(queries: DataSource, schema: DataSource)(using timer: com.histmersion.util.time.Timer): QueryService =
    Implementation(Transactor(queries), Transactor(schema))

  final private class Implementation(queries: Transactor, schema: Transactor)(using timer: com.histmersion.util.time.Timer) extends QueryService:
    def run(statement: com.histmersion.domain.library.search.QueryService.Implementation.RunStatement, timeoutMs: com.histmersion.domain.library.search.QueryService.Implementation.RunTimeoutMs, maxRows: com.histmersion.domain.library.search.QueryService.Implementation.RunMaxRows, maxCellChars: com.histmersion.domain.library.search.QueryService.Implementation.RunMaxCellChars): Either[com.histmersion.domain.library.search.QueryService.Implementation.RunResultPart1, QueryResult] =
      try queries.readOnlyRollback(timeoutMs)(QueryRepo.run(statement, maxRows, maxCellChars))
      catch case error: java.sql.SQLException => Left(QueryRepo.message(error))
    def tables: List[SchemaTable] = schema.read(QueryRepo.tables)

  object Implementation:
    type RunResultPart1 = RunResultPart1.T
    object RunResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunStatement = RunStatement.T
    object RunStatement extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunTimeoutMs = RunTimeoutMs.T
    object RunTimeoutMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunMaxRows = RunMaxRows.T
    object RunMaxRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunMaxCellChars = RunMaxCellChars.T
    object RunMaxCellChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
