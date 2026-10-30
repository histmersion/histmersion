package com.histmersion.api

enum PluginState:
  case Registered

  case Disabled

  case Failed

  case Refused

  def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

object PluginState:
  def parse(s: String): Option[PluginState] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
