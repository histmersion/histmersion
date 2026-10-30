package com.histmersion.api

object UpstreamQuotaMarkdown:

  private def name(upstream: String): String = if upstream == "core" then "CORE" else upstream

  /** `CORE today (UTC 2026-10-26): 14 of 900 tokens — PDF lookups 10, searches 4 of 600 · at most 20 a minute · CORE's
    * window 18 of 25 left · BLOCKED until … (HTTP 429)`. Pure.
    */
  def line(q: UpstreamQuota): String =
    val spent = q.lookupTokens + q.searchTokens
    val window = q.rateRemaining.fold("")(r => s" · CORE's window $r of ${q.rateLimit.fold("?")(_.toString)} left")
    val block =
      q.blockedUntil.fold("")(u => s" · **not asked until $u** (${q.blockedWhy.getOrElse("CORE asked to wait")})")
    s"${name(q.upstream)} today (UTC ${q.day}): $spent of ${q.dailyTokens} tokens — PDF lookups ${q.lookupTokens}, " +
      s"searches ${q.searchTokens} of ${q.searchDailyTokens} · at most ${q.perMinute} a minute$window$block"
