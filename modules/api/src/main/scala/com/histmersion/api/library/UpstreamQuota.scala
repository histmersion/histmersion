package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class UpstreamQuota(
    upstream: UpstreamQuota.Upstream,
    day: UpstreamQuota.Day,
    lookupTokens: UpstreamQuota.LookupTokens,
    searchTokens: UpstreamQuota.SearchTokens,
    dailyTokens: UpstreamQuota.DailyTokens,
    searchDailyTokens: UpstreamQuota.SearchDailyTokens,
    perMinute: UpstreamQuota.PerMinute,
    blockedUntil: Option[UpstreamQuota.BlockedUntil] = None,
    blockedWhy: Option[UpstreamQuota.BlockedWhy] = None,
    rateLimit: Option[UpstreamQuota.RateLimit] = None,
    rateRemaining: Option[UpstreamQuota.RateRemaining] = None,
    observedAt: Option[UpstreamQuota.ObservedAt] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object UpstreamQuota:
  type Upstream = Upstream.T
  object Upstream extends RefinedType[String, Pure]
  type Day = Day.T
  object Day extends RefinedType[String, Pure]
  type LookupTokens = LookupTokens.T
  object LookupTokens extends RefinedType[Int, Pure]
  type SearchTokens = SearchTokens.T
  object SearchTokens extends RefinedType[Int, Pure]
  type DailyTokens = DailyTokens.T
  object DailyTokens extends RefinedType[Int, Pure]
  type SearchDailyTokens = SearchDailyTokens.T
  object SearchDailyTokens extends RefinedType[Int, Pure]
  type PerMinute = PerMinute.T
  object PerMinute extends RefinedType[Int, Pure]
  type BlockedUntil = BlockedUntil.T
  object BlockedUntil extends RefinedType[String, Pure]
  type BlockedWhy = BlockedWhy.T
  object BlockedWhy extends RefinedType[String, Pure]
  type RateLimit = RateLimit.T
  object RateLimit extends RefinedType[Int, Pure]
  type RateRemaining = RateRemaining.T
  object RateRemaining extends RefinedType[Int, Pure]
  type ObservedAt = ObservedAt.T
  object ObservedAt extends RefinedType[String, Pure]
