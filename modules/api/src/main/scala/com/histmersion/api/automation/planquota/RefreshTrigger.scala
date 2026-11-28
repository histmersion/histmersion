package com.histmersion
package api.automation.planquota

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

enum RefreshTrigger(val wire: RefreshTrigger.Wire):
  case Pick extends RefreshTrigger(RefreshTrigger.Wire("pick"))

  case Paused extends RefreshTrigger(RefreshTrigger.Wire("paused-run"))

  case Command extends RefreshTrigger(RefreshTrigger.Wire("command"))

  case Schedule extends RefreshTrigger(RefreshTrigger.Wire("schedule"))

object RefreshTrigger:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
