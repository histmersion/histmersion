package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.library.acquisition.State
import com.histmersion.domain.library.acquisition.Backoff
import com.histmersion.domain.library.acquisition.Window

import com.histmersion.domain.CoreQuota

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.augustnagro.magnum.*
import java.time.{Instant, LocalDate}
import com.histmersion.domain.automation.ProviderKind
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** A metered upstream's budget (migration V103), one row per upstream. The recent call slots (pacing within a minute)
  * are not kept: see [[CoreQuota.State]]. Every method runs on the connection it is given.
  */
@atBoundary(BoundaryKind.Sql, "reads and writes the upstream quota tables and their row codecs")
private[acquisition] object UpstreamQuotaRepo:

  // Times cross as epoch milliseconds (no timestamp codec in the native image), a day as its ISO text.

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def load(upstream: ProviderKind.Wire)(using DbCon): Option[State] =
    sql"""SELECT day::text, lookup_tokens, search_tokens, (extract(epoch FROM blocked_until) * 1000)::bigint, blocked_why,
                 rate_limit, rate_remaining, (extract(epoch FROM observed_at) * 1000)::bigint
          FROM upstream_quota WHERE upstream = $upstream"""
      .query[(String, Int, Int, Option[Long], Option[String], Option[Int], Option[Int], Option[Long])]
      .run()
      .headOption
      .map { (day, lookups, searches, until, why, limit, remaining, at) =>
        State(
          LocalDate.parse(day),
          State.LookupTokens(lookups),
          State.SearchTokens(searches),
          until.map(u =>
            Backoff(
              Instant.ofEpochMilli(u),
              why.fold(Backoff.Rationale.Unstated)(Backoff.Rationale.read)
            )
          ),
          at.map(t =>
            Window(
              limit.map(Window.Limit(_)),
              remaining.map(Window.Remaining(_)),
              Instant.ofEpochMilli(t)
            )
          )
        )
      }

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def save(upstream: ProviderKind.Wire, s: State)(using DbCon): Unit =
    val day = s.day.toString
    val until = s.block.map(_.until.toEpochMilli.toDouble / 1000)
    val why = s.block.map(_.why.said)
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
