package com.histmersion
package api.automation.modelchains

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Wording

enum SettingSource(val wire: SettingSource.Wire):
  case Config extends SettingSource(SettingSource.Wire("config"))
  case Saved extends SettingSource(SettingSource.Wire("saved"))

object SettingSource:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]:
    given Display[T] = Display(wire => Wording.humanize(Wording.Id(wire.value)))
