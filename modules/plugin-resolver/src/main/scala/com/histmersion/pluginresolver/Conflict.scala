package com.histmersion.pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** A plugin needs a module the server ships, at a NEWER version than the server's. One class loader holds one version,
  * and the server's would win, so the plugin cannot be linked.
  */
final case class Conflict(plugin: Conflict.Plugin, key: ModuleKey, wanted: ModuleVersion, shipped: ModuleVersion):
  def message: com.histmersion.pluginresolver.Conflict.MessageResult =
    _root_.com.histmersion.pluginresolver.Resolver.Error(s"$plugin needs $key ${wanted.value}, but this Histmersion ships ${shipped.value} — " +
      s"build the plugin against ${shipped.value} (the histmersion-plugin-platform POM), or install a Histmersion that ships ${wanted.value}")

object Conflict:
  type MessageResult = _root_.com.histmersion.pluginresolver.Resolver.Error
  val MessageResult: _root_.com.histmersion.pluginresolver.Resolver.Error.type = _root_.com.histmersion.pluginresolver.Resolver.Error

  type Plugin = _root_.com.histmersion.pluginresolver.Resolver.FileName
  val Plugin: _root_.com.histmersion.pluginresolver.Resolver.FileName.type = _root_.com.histmersion.pluginresolver.Resolver.FileName
