package com.histmersion
package api.automation.planquota

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

enum RefreshMode(val wire: RefreshMode.Wire) derives CanEqual:
  case Auto extends RefreshMode(RefreshMode.Wire("auto"))
  case Probe extends RefreshMode(RefreshMode.Wire("probe"))
  case Off extends RefreshMode(RefreshMode.Wire("off"))

object RefreshMode:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[RefreshMode] =
    values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
