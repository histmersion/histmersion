package com.histmersion
package api.automation.planquota

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.PlanQuota.Rationale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.json.TwoStates

final case class PoolStatus(
    pool: PoolStatus.Pool,
    title: PoolStatus.Title,
    kind: PoolStatus.Kind,
    covers: List[PoolStatus.Covers],
    floors: PoolStatus.Floors,
    floorsSource: PoolStatus.FloorsSource,
    held: PoolStatus.Held,
    usable: PoolStatus.Usable,
    reason: Option[Rationale],
    until: Option[PoolStatus.Until],
    maxCallsInFlight: PoolStatus.MaxCallsInFlight,
    windows: List[WindowStatus],
    planType: Option[PoolStatus.PlanType] = None,
    note: Option[PoolStatus.Note] = None,
    available: PoolStatus.Available = PoolStatus.Available.Available,
    login: Option[PoolStatus.Login] = None,
    refresh: Option[PoolStatus.Refresh] = None,
    refreshesToday: PoolStatus.RefreshesToday = PoolStatus.RefreshesToday(0),
    lastRefresh: Option[RefreshInfo] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object PoolStatus:
  export PoolId.{Title, Wire as Pool}
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Covers = Covers.T
  object Covers extends Newtype[String, Pure]
  type Floors = Floors.T
  object Floors extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type FloorsSource = FloorsSource.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object FloorsSource extends Newtype[String, Pure]
  enum Held derives CanEqual:
    case Held, Released
  object Held extends TwoStates[Held]:
    extension (held: Held) def held: Boolean = held == Held
  enum Usable derives CanEqual:
    case Usable, Unusable
  object Usable extends TwoStates[Usable]:
    extension (usable: Usable) def usable: Boolean = usable == Usable
  type Until = Until.T
  object Until extends Newtype[String, Pure]
  type MaxCallsInFlight = MaxCallsInFlight.T
  object MaxCallsInFlight extends Newtype[Int, Pure]
  type PlanType = PlanType.T
  object PlanType extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  enum Available derives CanEqual:
    case Available, Unavailable
  object Available extends TwoStates[Available]:
    extension (available: Available) def available: Boolean = available == Available
  type Login = Login.T
  object Login extends Newtype[String, Pure]
  type Refresh = Refresh.T
  object Refresh extends Newtype[String, Pure]
  type RefreshesToday = RefreshesToday.T
  object RefreshesToday extends Newtype[Int, Pure]
