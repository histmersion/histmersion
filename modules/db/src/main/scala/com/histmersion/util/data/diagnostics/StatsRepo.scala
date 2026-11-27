package com.histmersion
package util.data.diagnostics

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.data.TableName
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The counts and sizes the diagnostics read: rows of a table, the database's size and its tables'. Plain `count(*)`s
  * over whole tables — the About panel is opened by hand, not polled.
  */
@atBoundary(BoundaryKind.Sql, "counts rows and measures table sizes for the diagnostics")
object StatsRepo:
  type ColumnName = ColumnName.T
  object ColumnName extends Newtype[String, Pure]
  type Count = Count.T
  object Count extends Newtype[Long, Pure]

  /** The rows of `table`. */

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G58Raw, "pending G58 (SQL text only through the codecs)")
  def rows(table: TableName)(using DbCon): Count =
    Count(sql"SELECT count(*)::int FROM ${SqlLiteral(quoted(table.value))}".query[Int].run().head.toLong)

  /** The rows of `table` whose `column` is set. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  @tolerated(LintRule.G58Raw, "pending G58 (SQL text only through the codecs)")
  def rowsWith(table: TableName, column: ColumnName)(using DbCon): Count =
    Count(
      sql"SELECT count(*)::int FROM ${SqlLiteral(quoted(table.value))} WHERE ${SqlLiteral(quoted(column.value))} IS NOT NULL"
        .query[Int]
        .run()
        .head
        .toLong
    )

  /** The database's name and its size on disk (`pg_database_size`: tables, indexes, TOAST — what a dump would read
    * back, plus the free space inside the files).
    */
  def databaseSize(using DbCon): (TableName, ByteCount) =
    sql"SELECT current_database()::text, pg_database_size(current_database())".query[(TableName, ByteCount)].run().head

  /** Every table of the database by `pg_total_relation_size` (indexes and TOAST included), largest first. */
  @tolerated(LintRule.TotalOrder, "pg_class of the user schemas, grouped by relation: column 1 is unique")
  def tableBytes(using DbCon): List[(TableName, ByteCount)] =
    sql"""SELECT c.relname::text, pg_total_relation_size(c.oid)
            FROM pg_class c JOIN pg_namespace ns ON ns.oid = c.relnamespace
           WHERE c.relkind IN ('r', 'm') AND ns.nspname NOT IN ('pg_catalog', 'information_schema')
             AND ns.nspname NOT LIKE 'pg_toast%'
           ORDER BY 2 DESC, 1"""
      .query[(TableName, ByteCount)]
      .run()
      .toList

  private def quoted(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""
