package com.histmersion
package domain.library.search

import com.histmersion.api.library.query.QueryResult
import com.histmersion.api.library.query.SchemaTable
import com.histmersion.util.problem.Problem

import com.histmersion.util.database.Transactor

import javax.sql.DataSource
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.time.Timer
import com.histmersion.lint.{tolerated, LintRule}

/** The read-only SQL escape hatch and the live schema it reads. A statement runs on the read-only role's pool
  * (`queries`) in a read-only transaction of its own, with a timeout and caps; the schema is read on the application's
  * pool (`schema`). A mechanism over JDBC, so it has no in-memory twin.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.Service, "pending G16 (a service is a trait with Implementation and InMemory)")
trait QueryService:
  def run(
      statement: QueryRepo.Statement,
      timeoutMs: Transactor.TimeoutMs,
      maxRows: QueryRepo.MaxRows,
      maxCellChars: QueryRepo.MaxCellChars
  ): Either[Problem, QueryResult]
  def tables: List[SchemaTable]

object QueryService:

  def apply(queries: DataSource, schema: DataSource)(using timer: Timer): QueryService =
    Implementation(Transactor(queries), Transactor(schema))

  final private class Implementation(queries: Transactor, schema: Transactor)(using timer: Timer) extends QueryService:
    def run(
        statement: QueryRepo.Statement,
        timeoutMs: Transactor.TimeoutMs,
        maxRows: QueryRepo.MaxRows,
        maxCellChars: QueryRepo.MaxCellChars
    ): Either[Problem, QueryResult] =
      try
        queries
          .readOnlyRollback(timeoutMs)(QueryRepo.run(statement, maxRows, maxCellChars))
      catch case error: java.sql.SQLException => Left(QueryRepo.message(error))
    def tables: List[SchemaTable] = schema.read(QueryRepo.tables)
