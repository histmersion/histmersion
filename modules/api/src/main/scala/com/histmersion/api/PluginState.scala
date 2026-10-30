package com.histmersion.api

/** How a plugin ended up. */
enum PluginState:
  /** Enabled, decoded, its secrets resolved, `register` succeeded: its contributions are live. */
  case Registered

  /** Not enabled (`histmersion.plugins.<id>.enabled = false`, the default for first-party plugins). */
  case Disabled

  /** Enabled, but its block, its secrets or its `register` failed: it contributes nothing. */
  case Failed

  /** Not loaded at all: a foreign SPI version, an id used twice, an id that is no config key. */
  case Refused

  def wire: String = toString.toLowerCase(java.util.Locale.ROOT)

object PluginState:
  def parse(s: String): Option[PluginState] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))
