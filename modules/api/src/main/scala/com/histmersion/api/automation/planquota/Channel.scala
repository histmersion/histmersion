package com.histmersion
package api.automation.planquota

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

enum Channel(val wire: Channel.Wire) derives CanEqual:
  case Run extends Channel(Channel.Wire("run"))

  case Mod extends Channel(Channel.Wire("mod"))

  case Interactive extends Channel(Channel.Wire("interactive"))

  case AppServer extends Channel(Channel.Wire("app-server"))

  case Limit extends Channel(Channel.Wire("limit"))

  case Probe extends Channel(Channel.Wire("probe"))

object Channel:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Channel] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
