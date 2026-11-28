package com.histmersion
package api.automation.managers

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Managers.CurrencyCheck
import com.histmersion.api.automation.Managers.AcquisitionSweep
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

enum Sweep(val wire: Sweep.Wire, val itemKind: ManagerItem.Kind) derives CanEqual:
  case Acquisition extends Sweep(Sweep.Wire("acquisition"), ManagerItem.Kind(AcquisitionSweep))
  case Currency extends Sweep(Sweep.Wire("currency"), ManagerItem.Kind(CurrencyCheck))

object Sweep:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Sweep] =
    values.find(x =>
      x.wire == Wire((s).value.trim.toLowerCase) || x.itemKind == ManagerItem.Kind((s).value.trim.toLowerCase)
    )
  def ofItem(kind: ManagerItem.Kind): Option[Sweep] = values.find(_.itemKind == kind)

  def of(c: ManagerConfig): List[Sweep] =
    c.sweeps.fold(values.toList)(ws => values.toList.filter(s => ws.exists(w => parse(w).contains(s))))
