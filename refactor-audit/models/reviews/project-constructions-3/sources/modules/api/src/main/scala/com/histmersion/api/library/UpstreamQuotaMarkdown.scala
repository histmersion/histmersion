package com.histmersion.api.library
import com.histmersion.api.given
object UpstreamQuotaMarkdown:

  type NameResult = NameResult.T
  object NameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NameUpstream = NameUpstream.T
  object NameUpstream extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LineResult = LineResult.T
  object LineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private def name(upstream: com.histmersion.api.library.UpstreamQuotaMarkdown.NameUpstream): com.histmersion.api.library.UpstreamQuotaMarkdown.NameResult = if upstream == _root_.com.histmersion.api.library.UpstreamQuotaMarkdown.NameUpstream("core") then _root_.com.histmersion.api.library.UpstreamQuotaMarkdown.NameResult("CORE") else upstream

  def line(q: UpstreamQuota): com.histmersion.api.library.UpstreamQuotaMarkdown.LineResult =
    val spent = q.lookupTokens + q.searchTokens
    val window = q.rateRemaining.fold("")(r => s" · CORE's window $r of ${q.rateLimit.fold("?")(_.toString)} left")
    val block =
      q.blockedUntil.fold("")(u => s" · **not asked until $u** (${q.blockedWhy.getOrElse("CORE asked to wait")})")
    s"${name(q.upstream)} today (UTC ${q.day}): $spent of ${q.dailyTokens} tokens — PDF lookups ${q.lookupTokens}, " +
      s"searches ${q.searchTokens} of ${q.searchDailyTokens} · at most ${q.perMinute} a minute$window$block"
