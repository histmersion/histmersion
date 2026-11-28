package com.histmersion
package api.automation.planquota

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.PlanQuota.Rationale
import com.histmersion.api.json.TwoStates

final case class WindowState(
    reading: Reading,
    floor: WindowState.Floor,
    leftPct: WindowState.LeftPct,
    stale: WindowState.Stale,
    usable: WindowState.Usable,
    why: Option[Rationale]
)

object WindowState:
  export Floors.Floor
  export WindowStatus.LeftPct
  enum Stale derives CanEqual:
    case Stale, Fresh
  object Stale extends TwoStates[Stale]:
    extension (stale: Stale) def stale: Boolean = stale == Stale
  enum Usable derives CanEqual:
    case Usable, Unusable
  object Usable extends TwoStates[Usable]:
    extension (usable: Usable) def usable: Boolean = usable == Usable
