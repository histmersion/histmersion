package com.histmersion.util.data.diagnostics

import com.augustnagro.magnum.*
import com.histmersion.db.Sql

/** The counts and sizes the diagnostics read: rows of a table, the jobs by state, the database's size and its tables'.
  * Plain `count(*)`s over whole tables — the About panel is opened by hand, not polled.
  */
object StatsRepo:

  /** The rows of `table`. */
  def rows(table: String)(using DbCon): Long =
    Sql.raw(s"SELECT count(*)::int FROM ${quoted(table)}").frag.query[Int].run().head.toLong

  /** The rows of `table` whose `column` is set. */
  def rowsWith(table: String, column: String)(using DbCon): Long =
    Sql.raw(s"SELECT count(*)::int FROM ${quoted(table)} WHERE ${quoted(column)} IS NOT NULL").frag
      .query[Int]
      .run()
      .head
      .toLong

  /** The job queue by state. */
  def jobStates(using DbCon): List[(String, Int)] =
    sql"SELECT status, count(*)::int FROM jobs GROUP BY status".query[(String, Int)].run().toList

  /** The database's name and its size on disk (`pg_database_size`: tables, indexes, TOAST — what a dump would read
    * back, plus the free space inside the files).
    */
  def databaseSize(using DbCon): (String, Long) =
    sql"SELECT current_database()::text, pg_database_size(current_database())".query[(String, Long)].run().head

  /** Every table of the database by `pg_total_relation_size` (indexes and TOAST included), largest first. */
  def tableBytes(using DbCon): List[(String, Long)] =
    sql"""SELECT c.relname::text, pg_total_relation_size(c.oid)
            FROM pg_class c JOIN pg_namespace ns ON ns.oid = c.relnamespace
           WHERE c.relkind IN ('r', 'm') AND ns.nspname NOT IN ('pg_catalog', 'information_schema')
             AND ns.nspname NOT LIKE 'pg_toast%'
           ORDER BY 2 DESC, 1"""
      .query[(String, Long)]
      .run()
      .toList

  private def quoted(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""
