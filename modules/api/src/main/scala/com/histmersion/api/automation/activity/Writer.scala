package com.histmersion
package api.automation.activity

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

enum Writer(val wire: Writer.Wire):
  case Agent extends Writer(Writer.Wire("agent"))
  case Job extends Writer(Writer.Wire("job"))

object Writer:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
