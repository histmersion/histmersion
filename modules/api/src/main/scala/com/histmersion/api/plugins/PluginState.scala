package com.histmersion.api.plugins
import com.histmersion.api.given
enum PluginState:
  case Registered

  case Disabled

  case Failed

  case Refused

  def wire: com.histmersion.api.plugins.PluginState.Wire = _root_.com.histmersion.api.plugins.PluginState.Wire(toString.toLowerCase(java.util.Locale.ROOT))

object PluginState:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.plugins.PluginState.Wire): Option[PluginState] = values.find(_.wire == _root_.com.histmersion.api.plugins.PluginState.Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
