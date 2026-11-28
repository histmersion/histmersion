package com.histmersion
package api.automation.planquota

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

enum Source(val wire: Source.Wire) derives CanEqual:
  case Run extends Source(Source.Wire("run"))

  case Mod extends Source(Source.Wire("mod"))

  case Interactive extends Source(Source.Wire("interactive"))

  case AppServer extends Source(Source.Wire("app-server"))

  case Limit extends Source(Source.Wire("limit"))

  case Probe extends Source(Source.Wire("probe"))

object Source:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Source] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
