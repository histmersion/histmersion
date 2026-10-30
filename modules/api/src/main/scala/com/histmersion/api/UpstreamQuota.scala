package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

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
