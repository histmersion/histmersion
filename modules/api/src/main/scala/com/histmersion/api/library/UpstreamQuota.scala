package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
      KindlingsSchema, com.histmersion.api.json.Schema
