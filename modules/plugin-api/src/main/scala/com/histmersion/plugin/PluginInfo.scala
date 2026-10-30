package com.histmersion.plugin

import scala.compiletime.deferred

/** @param id
  *   the config key (`histmersion.plugins.<id>`) and the name in logs and reports; kebab-case, `[a-z0-9][a-z0-9-]*`
  * @param version
  *   the plugin's own version, as reported
  * @param spiVersion
  *   the SPI generation the plugin was compiled against ([[Spi.Version]]); the host refuses any other
  */
final case class PluginInfo(id: String, version: String, spiVersion: Int)
