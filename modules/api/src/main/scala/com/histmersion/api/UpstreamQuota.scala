package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** A metered upstream's budget today, as `histmersion about` and the autopilot status show it. Today only CORE
  * (`upstream = "core"`): the tokens its two uses spent on `day` (a UTC day) — the acquisition chain's PDF lookups and
  * the new-source search's queries — against `acquire.core.daily-tokens` and the searches' own cap; the pace; and,
  * while CORE asked to wait, until when and why. `rateLimit` / `rateRemaining` are what CORE's last answer (at
  * `observedAt`) said of its current window.
  */
final case class UpstreamQuota(
    upstream: String,
    day: String,
    lookupTokens: Int,
    searchTokens: Int,
    dailyTokens: Int,
    searchDailyTokens: Int,
    perMinute: Int,
    blockedUntil: Option[String] = None,
    blockedWhy: Option[String] = None,
    rateLimit: Option[Int] = None,
    rateRemaining: Option[Int] = None,
    observedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object UpstreamQuota:
  given Schema[UpstreamQuota] = summon[KindlingsSchema[UpstreamQuota]].schema

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
