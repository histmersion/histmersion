package com.histmersion.util.data.diagnostics

import com.augustnagro.magnum.*
import com.histmersion.util.database.Sql
import com.histmersion.util.database.NewtypeColumns.given

/** The counts and sizes the diagnostics read: rows of a table, the database's size and its tables'. Plain `count(*)`s
  * over whole tables — the About panel is opened by hand, not polled.
  */
object StatsRepo:
  type ColumnName = ColumnName.T
  object ColumnName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The rows of `table`. */

  def rows(table: com.histmersion.util.data.TableName)(using DbCon): com.histmersion.util.data.diagnostics.StatsRepo.Count =
    StatsRepo.Count(Sql.raw(Sql.RawText(s"SELECT count(*)::int FROM ${quoted(table.value)}")).frag.query[Int].run().head.toLong)

  /** The rows of `table` whose `column` is set. */
  def rowsWith(table: com.histmersion.util.data.TableName, column: com.histmersion.util.data.diagnostics.StatsRepo.ColumnName)(using DbCon): com.histmersion.util.data.diagnostics.StatsRepo.Count =
    StatsRepo.Count(
      Sql
        .raw(Sql.RawText(s"SELECT count(*)::int FROM ${quoted(table.value)} WHERE ${quoted(column.value)} IS NOT NULL"))
        .frag
        .query[Int]
        .run()
        .head
        .toLong
    )

  /** The database's name and its size on disk (`pg_database_size`: tables, indexes, TOAST — what a dump would read
    * back, plus the free space inside the files).
    */
  def databaseSize(using DbCon): (com.histmersion.util.data.TableName, com.histmersion.ByteCount) =
    sql"SELECT current_database()::text, pg_database_size(current_database())".query[(com.histmersion.util.data.TableName, com.histmersion.ByteCount)].run().head

  /** Every table of the database by `pg_total_relation_size` (indexes and TOAST included), largest first. */
  def tableBytes(using DbCon): List[(com.histmersion.util.data.TableName, com.histmersion.ByteCount)] =
    sql"""SELECT c.relname::text, pg_total_relation_size(c.oid)
            FROM pg_class c JOIN pg_namespace ns ON ns.oid = c.relnamespace
           WHERE c.relkind IN ('r', 'm') AND ns.nspname NOT IN ('pg_catalog', 'information_schema')
             AND ns.nspname NOT LIKE 'pg_toast%'
           ORDER BY 2 DESC, 1"""
      .query[(com.histmersion.util.data.TableName, com.histmersion.ByteCount)]
      .run()
      .toList

  private def quoted(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""
