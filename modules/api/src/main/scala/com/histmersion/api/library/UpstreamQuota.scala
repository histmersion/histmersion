package com.histmersion
package api.library

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import UpstreamQuota.*

final case class UpstreamQuota(
    upstream: Upstream,
    day: Day,
    lookupTokens: LookupTokens,
    searchTokens: SearchTokens,
    dailyTokens: DailyTokens,
    searchDailyTokens: SearchDailyTokens,
    perMinute: PerMinute,
    blockedUntil: Option[BlockedUntil] = None,
    blockedWhy: Option[BlockedBy] = None,
    rateLimit: Option[RateLimit] = None,
    rateRemaining: Option[RateRemaining] = None,
    observedAt: Option[ObservedAt] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object UpstreamQuota:
  type Upstream = Upstream.T
  object Upstream extends Newtype[String, Pure]
  type Day = Day.T
  object Day extends Newtype[String, Pure]
  type LookupTokens = LookupTokens.T
  object LookupTokens extends Newtype[Int, Pure]
  type SearchTokens = SearchTokens.T
  object SearchTokens extends Newtype[Int, Pure]
  type DailyTokens = DailyTokens.T
  object DailyTokens extends Newtype[Int, Pure]
  type SearchDailyTokens = SearchDailyTokens.T
  object SearchDailyTokens extends Newtype[Int, Pure]
  type PerMinute = PerMinute.T
  object PerMinute extends Newtype[Int, Pure]
  type BlockedUntil = BlockedUntil.T
  object BlockedUntil extends Newtype[String, Pure]

  /** What the upstream answered that blocks it (`HTTP 429`). */
  type BlockedBy = BlockedBy.T
  object BlockedBy extends Newtype[String, Pure]
  type RateLimit = RateLimit.T
  object RateLimit extends Newtype[Int, Pure]
  type RateRemaining = RateRemaining.T
  object RateRemaining extends Newtype[Int, Pure]
  type ObservedAt = ObservedAt.T
  object ObservedAt extends Newtype[String, Pure]
