package com.histmersion.api

object UpstreamQuotaMarkdown:

  private def name(upstream: String): String = if upstream == "core" then "CORE" else upstream

  def line(q: UpstreamQuota): String =
    val spent = q.lookupTokens + q.searchTokens
    val window = q.rateRemaining.fold("")(r => s" · CORE's window $r of ${q.rateLimit.fold("?")(_.toString)} left")
    val block =
      q.blockedUntil.fold("")(u => s" · **not asked until $u** (${q.blockedWhy.getOrElse("CORE asked to wait")})")
    s"${name(q.upstream)} today (UTC ${q.day}): $spent of ${q.dailyTokens} tokens — PDF lookups ${q.lookupTokens}, " +
      s"searches ${q.searchTokens} of ${q.searchDailyTokens} · at most ${q.perMinute} a minute$window$block"
