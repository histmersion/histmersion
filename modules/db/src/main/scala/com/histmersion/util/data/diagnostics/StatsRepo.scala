package com.histmersion.util.data.diagnostics

import com.augustnagro.magnum.*
import com.histmersion.util.database.Sql

/** The counts and sizes the diagnostics read: rows of a table, the database's size and its tables'. Plain `count(*)`s
  * over whole tables — the About panel is opened by hand, not polled.
  */
object StatsRepo:

  /** The rows of `table`. */
  type RowsResult = RowsResult.T
  object RowsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowsTable = RowsTable.T
  object RowsTable extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowsWithResult = RowsWithResult.T
  object RowsWithResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowsWithTable = RowsWithTable.T
  object RowsWithTable extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RowsWithColumn = RowsWithColumn.T
  object RowsWithColumn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatabaseSizeResultPart1 = DatabaseSizeResultPart1.T
  object DatabaseSizeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatabaseSizeResultPart2 = DatabaseSizeResultPart2.T
  object DatabaseSizeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableBytesResultPart1 = TableBytesResultPart1.T
  object TableBytesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TableBytesResultPart2 = TableBytesResultPart2.T
  object TableBytesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuotedResult = QuotedResult.T
  object QuotedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuotedName = QuotedName.T
  object QuotedName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def rows(table: com.histmersion.util.data.diagnostics.StatsRepo.RowsTable)(using DbCon): com.histmersion.util.data.diagnostics.StatsRepo.RowsResult =
    Sql.raw(s"SELECT count(*)::int FROM ${quoted(table)}").frag.query[Int].run().head.toLong

  /** The rows of `table` whose `column` is set. */
  def rowsWith(table: com.histmersion.util.data.diagnostics.StatsRepo.RowsWithTable, column: com.histmersion.util.data.diagnostics.StatsRepo.RowsWithColumn)(using DbCon): com.histmersion.util.data.diagnostics.StatsRepo.RowsWithResult =
    Sql
      .raw(s"SELECT count(*)::int FROM ${quoted(table)} WHERE ${quoted(column)} IS NOT NULL")
      .frag
      .query[Int]
      .run()
      .head
      .toLong

  /** The database's name and its size on disk (`pg_database_size`: tables, indexes, TOAST — what a dump would read
    * back, plus the free space inside the files).
    */
  def databaseSize(using DbCon): (com.histmersion.util.data.diagnostics.StatsRepo.DatabaseSizeResultPart1, com.histmersion.util.data.diagnostics.StatsRepo.DatabaseSizeResultPart2) =
    sql"SELECT current_database()::text, pg_database_size(current_database())".query[(String, Long)].run().head

  /** Every table of the database by `pg_total_relation_size` (indexes and TOAST included), largest first. */
  def tableBytes(using DbCon): List[(com.histmersion.util.data.diagnostics.StatsRepo.TableBytesResultPart1, com.histmersion.util.data.diagnostics.StatsRepo.TableBytesResultPart2)] =
    sql"""SELECT c.relname::text, pg_total_relation_size(c.oid)
            FROM pg_class c JOIN pg_namespace ns ON ns.oid = c.relnamespace
           WHERE c.relkind IN ('r', 'm') AND ns.nspname NOT IN ('pg_catalog', 'information_schema')
             AND ns.nspname NOT LIKE 'pg_toast%'
           ORDER BY 2 DESC, 1"""
      .query[(String, Long)]
      .run()
      .toList

  private def quoted(name: com.histmersion.util.data.diagnostics.StatsRepo.QuotedName): com.histmersion.util.data.diagnostics.StatsRepo.QuotedResult = "\"" + name.replace("\"", "\"\"") + "\""
