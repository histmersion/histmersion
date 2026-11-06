package com.histmersion.api.plugins
import com.histmersion.api.given
enum PluginState:
  case Registered

  case Disabled

  case Failed

  case Refused

  def wire: com.histmersion.api.plugins.PluginState.WireResult = _root_.com.histmersion.api.plugins.PluginState.WireResult(toString.toLowerCase(java.util.Locale.ROOT))

object PluginState:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.plugins.PluginState.ParseS): Option[PluginState] = values.find(_.wire == (s).value.trim.toLowerCase(java.util.Locale.ROOT))
