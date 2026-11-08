package com.histmersion
package api.plugins
import com.histmersion.api.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
enum PluginState:
  case Registered

  case Disabled

  case Failed

  case Refused

  def wire: PluginState.Wire = PluginState.Wire(toString.toLowerCase(java.util.Locale.ROOT))

object PluginState:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]

  def parse(s: Wire): Option[PluginState] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
