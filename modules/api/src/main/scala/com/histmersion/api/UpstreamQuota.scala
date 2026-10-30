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
