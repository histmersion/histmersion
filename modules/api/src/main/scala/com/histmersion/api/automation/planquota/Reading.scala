package com.histmersion
package api.automation.planquota

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates
import com.histmersion.api.automation.ModelChains
import com.histmersion.api.automation.modelchains.ChainEntry
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.PlanQuota.WindowStateNow
import com.histmersion.api.automation.PlanQuota.Rationale

final case class Reading(
    limit: Reading.Limit,
    kind: Reading.Kind,
    usedPct: Reading.UsedPct,
    resetsAt: Option[Reading.ResetsAt],
    rejected: Reading.Rejected,
    observedAt: Reading.ObservedAt,
    source: Source,
    model: Option[Reading.Model] = None
)

object Reading:
  type Limit = Limit.T
  object Limit extends Newtype[String, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type UsedPct = UsedPct.T
  object UsedPct extends Newtype[Double, Pure]
  type ResetsAt = ResetsAt.T
  object ResetsAt extends Newtype[Long, Pure]
  enum Rejected derives CanEqual:
    case Rejected, Accepted
  object Rejected extends TwoStates[Rejected]:
    extension (rejected: Rejected) def rejected: Boolean = rejected == Rejected
  type ObservedAt = ObservedAt.T
  object ObservedAt extends Newtype[Long, Pure]
  export ChainEntry.Model

  extension (r: Reading)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def windowState(rules: Rules, now: WindowStateNow): WindowState =
      val floor = rules.floors.of(r.limit, r.kind, rules.defaultFloor)
      val left = (100.0 - (r.usedPct).value).max(0)
      val stale = now.value - r.observedAt.value > rules.staleMs.value
      val pct = (d: Double) =>
        java.math.BigDecimal(d).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros.toPlainString
      val why =
        if (r.rejected) == Reading.Rejected.Rejected then Some(s"${r.kind}: the provider refuses it")
        else if !stale && left <= floor.value then Some(s"${r.kind}: ${pct(left)}% left, floor $floor%")
        else if stale && left < floor.value + rules.marginPct.value then
          Some(
            s"${r.kind}: ${pct(left)}% left when last read, floor $floor% (+${rules.marginPct} while the reading is stale)"
          )
        else None
      WindowState(
        r,
        floor,
        WindowStatus.LeftPct(left),
        (if stale then WindowState.Stale.Stale else WindowState.Stale.Fresh),
        (if why.isEmpty then WindowState.Usable.Usable else WindowState.Usable.Unusable),
        why.map(w => Rationale(s"$w"))
      )
