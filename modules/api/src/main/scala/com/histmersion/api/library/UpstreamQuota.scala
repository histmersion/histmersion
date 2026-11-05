package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

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
  object Upstream extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Day = Day.T
  object Day extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LookupTokens = LookupTokens.T
  object LookupTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchTokens = SearchTokens.T
  object SearchTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DailyTokens = DailyTokens.T
  object DailyTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchDailyTokens = SearchDailyTokens.T
  object SearchDailyTokens extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PerMinute = PerMinute.T
  object PerMinute extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlockedUntil = BlockedUntil.T
  object BlockedUntil extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BlockedWhy = BlockedWhy.T
  object BlockedWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RateLimit = RateLimit.T
  object RateLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RateRemaining = RateRemaining.T
  object RateRemaining extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ObservedAt = ObservedAt.T
  object ObservedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
