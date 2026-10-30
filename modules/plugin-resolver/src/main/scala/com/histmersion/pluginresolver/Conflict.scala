package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** A plugin needs a module the server ships, at a NEWER version than the server's. One class loader holds one version,
  * and the server's would win, so the plugin cannot be linked.
  */
final case class Conflict(plugin: String, key: ModuleKey, wanted: ModuleVersion, shipped: ModuleVersion):
  def message: String =
    s"$plugin needs $key ${wanted.value}, but this Histmersion ships ${shipped.value} — " +
      s"build the plugin against ${shipped.value} (the histmersion-plugin-platform POM), or install a Histmersion that ships ${wanted.value}"
