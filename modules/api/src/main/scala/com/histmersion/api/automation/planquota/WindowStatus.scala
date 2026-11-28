package com.histmersion
package api.automation.planquota

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.PlanQuota.Rationale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class WindowStatus(
    limit: WindowStatus.Limit,
    kind: WindowStatus.Kind,
    usedPct: WindowStatus.UsedPct,
    leftPct: WindowStatus.LeftPct,
    floor: WindowStatus.Floor,
    usable: WindowStatus.Usable,
    stale: WindowStatus.Stale,
    resetsAt: Option[WindowStatus.ResetsAt],
    observedAt: WindowStatus.ObservedAt,
    ageSeconds: WindowStatus.AgeSeconds,
    source: WindowStatus.Source,
    why: Option[Rationale] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object WindowStatus:
  export Reading.Limit
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  export Reading.UsedPct
  type LeftPct = LeftPct.T
  object LeftPct extends Newtype[Double, Pure]
  export Floors.Floor
  enum Usable derives CanEqual:
    case Usable, Unusable
  object Usable extends TwoStates[Usable]:
    extension (usable: Usable) def usable: Boolean = usable == Usable
  enum Stale derives CanEqual:
    case Stale, Fresh
  object Stale extends TwoStates[Stale]:
    extension (stale: Stale) def stale: Boolean = stale == Stale
  type ResetsAt = ResetsAt.T
  object ResetsAt extends Newtype[String, Pure]
  type ObservedAt = ObservedAt.T
  object ObservedAt extends Newtype[String, Pure]
  type AgeSeconds = AgeSeconds.T
  object AgeSeconds extends Newtype[Long, Pure]
  type Source = Source.T
  object Source extends Newtype[String, Pure]
