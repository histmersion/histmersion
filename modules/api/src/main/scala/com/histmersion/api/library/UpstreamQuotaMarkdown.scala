package com.histmersion.api.library
import com.histmersion.api.given
object UpstreamQuotaMarkdown:

  type NameUpstream = _root_.com.histmersion.api.library.UpstreamQuota.Upstream
  val NameUpstream: _root_.com.histmersion.api.library.UpstreamQuota.Upstream.type = _root_.com.histmersion.api.library.UpstreamQuota.Upstream

  private def name(upstream: com.histmersion.api.library.UpstreamQuotaMarkdown.NameUpstream): String = if upstream == _root_.com.histmersion.api.library.UpstreamQuotaMarkdown.NameUpstream("core") then ("CORE") else upstream

  def line(q: UpstreamQuota): String =
    val spent = (q.lookupTokens).value + q.searchTokens
    val window = q.rateRemaining.fold("")(r => s" · CORE's window $r of ${q.rateLimit.fold("?")(_.toString)} left")
    val block =
      q.blockedUntil.fold("")(u => s" · **not asked until $u** (${q.blockedWhy.getOrElse("CORE asked to wait")})")
    (s"${name(q.upstream)} today (UTC ${q.day}): $spent of ${q.dailyTokens} tokens — PDF lookups ${q.lookupTokens}, " +
      s"searches ${q.searchTokens} of ${q.searchDailyTokens} · at most ${q.perMinute} a minute$window$block")
