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
      " (SELECT count(*)::int FROM places), (SELECT count(*)::int FROM periods)"

  def corpus(ds: DataSource): CorpusCounts =
    connect(ds):
      val (s, f, a, e, p, pr) = Sql.raw(corpusSql).frag
        .query[(Int, Int, Int, Int, Int, Int)].run().head
      CorpusCounts(s, f, a, e, p, pr)

  /** The OCR job queue by state. */
  def jobs(ds: DataSource): JobCounts =
    connect(ds):
      JobCounts.of(sql"SELECT status, count(*)::int FROM jobs GROUP BY status"
        .query[(String, Int)].run().toList)
