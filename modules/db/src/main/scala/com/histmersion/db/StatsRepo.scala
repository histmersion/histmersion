package com.histmersion.db

import com.histmersion.core.*

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The counts `GET /api/about` reports: how big the store is and where the OCR queue stands. Plain
  * `count(*)`s over whole tables — the About panel is opened by hand, not polled. */
object StatsRepo:

  /** The SQL of [[corpus]], as a value — one round trip, one row. */
  private[db] val corpusSql: String =
    "SELECT (SELECT count(*)::int FROM sources), (SELECT count(*)::int FROM findings)," +
      " (SELECT count(*)::int FROM articles), (SELECT count(*)::int FROM events)," +
      " (SELECT count(*)::int FROM places), (SELECT count(*)::int FROM periods)," +
      " (SELECT count(*)::int FROM periods WHERE preset IS NOT NULL)"

  def corpus(ds: DataSource): CorpusCounts =
    connect(ds):
      val (s, f, a, e, p, pr, ps) = Sql.raw(corpusSql).frag
        .query[(Int, Int, Int, Int, Int, Int, Int)].run().head
      CorpusCounts(s, f, a, e, p, pr, ps)

  /** The OCR job queue by state. */
  def jobs(ds: DataSource): JobCounts =
    connect(ds):
      JobCounts.of(sql"SELECT status, count(*)::int FROM jobs GROUP BY status"
        .query[(String, Int)].run().toList)

  /** The database's name and its size on disk (`pg_database_size`: tables, indexes, TOAST — what a
    * dump would read back, plus the free space inside the files). For the Storage part of About. */
  def databaseSize(ds: DataSource): (String, Long) =
    connect(ds):
      sql"SELECT current_database()::text, pg_database_size(current_database())"
        .query[(String, Long)].run().head

  /** The `n` largest tables of the database by `pg_total_relation_size` (indexes and TOAST
    * included), largest first — `sources` (the OCR'd bodies), `assets` (the figures), … */
  def largestTables(ds: DataSource, n: Int): List[TableSize] =
    if n <= 0 then Nil
    else connect(ds):
      sql"""SELECT c.relname::text, pg_total_relation_size(c.oid)
              FROM pg_class c JOIN pg_namespace ns ON ns.oid = c.relnamespace
             WHERE c.relkind IN ('r', 'm') AND ns.nspname NOT IN ('pg_catalog', 'information_schema')
               AND ns.nspname NOT LIKE 'pg_toast%'
             ORDER BY 2 DESC, 1 LIMIT $n"""
        .query[(String, Long)].run().toList.map(TableSize(_, _))
