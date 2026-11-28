package com.histmersion
package api.automation.planquota

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class Rules(
    floors: Floors,
    defaultFloor: Rules.DefaultFloor,
    staleMs: Rules.StaleMs,
    marginPct: Rules.MarginPct
)

object Rules:
  type MinGap = MinGap.T
  object MinGap extends Newtype[Long, Pure]
  export Floors.Floor as DefaultFloor
  type StaleMs = StaleMs.T
  object StaleMs extends Newtype[Long, Pure]
  type MarginPct = MarginPct.T
  object MarginPct extends Newtype[Int, Pure]
