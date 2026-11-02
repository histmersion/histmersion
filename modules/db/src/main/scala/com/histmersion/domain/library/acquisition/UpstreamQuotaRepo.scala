package com.histmersion.domain.library.acquisition

import com.histmersion.domain.CoreQuota

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import java.time.{Instant, LocalDate}

/** A metered upstream's budget (migration V103), one row per upstream. The recent call slots (pacing within a minute)
  * are not kept: see [[CoreQuota.State]]. Every method runs on the connection it is given.
  */
private[acquisition] object UpstreamQuotaRepo:

  // Times cross as epoch milliseconds (no timestamp codec in the native image), a day as its ISO text.

  def load(upstream: String)(using DbCon): Option[CoreQuota.State] =
    sql"""SELECT day::text, lookup_tokens, search_tokens, (extract(epoch FROM blocked_until) * 1000)::bigint, blocked_why,
                 rate_limit, rate_remaining, (extract(epoch FROM observed_at) * 1000)::bigint
          FROM upstream_quota WHERE upstream = $upstream"""
      .query[(String, Int, Int, Option[Long], Option[String], Option[Int], Option[Int], Option[Long])]
      .run()
      .headOption
      .map { (day, lookups, searches, until, why, limit, remaining, at) =>
        CoreQuota.State(
          LocalDate.parse(day),
          lookups,
          searches,
          until.map(u => CoreQuota.Block(Instant.ofEpochMilli(u), why.getOrElse("asked to wait"))),
          at.map(t => CoreQuota.Window(limit, remaining, Instant.ofEpochMilli(t)))
        )
      }

  def save(upstream: String, s: CoreQuota.State)(using DbCon): Unit =
    val day = s.day.toString
    val until = s.block.map(_.until.toEpochMilli.toDouble / 1000)
    val why = s.block.map(_.why)
    val limit = s.window.flatMap(_.limit)
    val remaining = s.window.flatMap(_.remaining)
    val at = s.window.map(_.at.toEpochMilli.toDouble / 1000)
    val _ =
      sql"""INSERT INTO upstream_quota (upstream, day, lookup_tokens, search_tokens, blocked_until, blocked_why, rate_limit,
                                        rate_remaining, observed_at, updated_at)
            VALUES ($upstream, $day::date, ${s.lookupTokens}, ${s.searchTokens}, to_timestamp($until::float8), $why, $limit,
                    $remaining, to_timestamp($at::float8), now())
            ON CONFLICT (upstream) DO UPDATE SET day = EXCLUDED.day, lookup_tokens = EXCLUDED.lookup_tokens,
              search_tokens = EXCLUDED.search_tokens, blocked_until = EXCLUDED.blocked_until, blocked_why = EXCLUDED.blocked_why,
              rate_limit = EXCLUDED.rate_limit, rate_remaining = EXCLUDED.rate_remaining, observed_at = EXCLUDED.observed_at,
              updated_at = now()""".update.run()
