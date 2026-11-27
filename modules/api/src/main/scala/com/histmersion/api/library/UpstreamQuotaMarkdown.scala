package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
object UpstreamQuotaMarkdown:

  export UpstreamQuota.Upstream as NameUpstream

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
  private def name(upstream: UpstreamQuotaMarkdown.NameUpstream): String =
    if upstream == UpstreamQuotaMarkdown.NameUpstream("core") then ("CORE") else upstream.value

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def line(q: UpstreamQuota): String =
    val spent = (q.lookupTokens).value + q.searchTokens.value
    val window = q.rateRemaining.fold("")(r => s" · CORE's window $r of ${q.rateLimit.fold("?")(_.toString)} left")
    val block =
      q.blockedUntil.fold("")(u =>
        s" · **not asked until $u** (${q.blockedWhy.fold("CORE asked to wait")(w => s"$w")})"
      )
    (s"${name(q.upstream)} today (UTC ${q.day}): $spent of ${q.dailyTokens} tokens — PDF lookups ${q.lookupTokens}, " +
      s"searches ${q.searchTokens} of ${q.searchDailyTokens} · at most ${q.perMinute} a minute$window$block")
