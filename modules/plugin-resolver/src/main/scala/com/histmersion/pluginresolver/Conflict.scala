package com.histmersion
package pluginresolver

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

/** A plugin needs a module the server ships, at a NEWER version than the server's. One class loader holds one version,
  * and the server's would win, so the plugin cannot be linked.
  */
final case class Conflict(plugin: Conflict.Plugin, key: ModuleKey, wanted: ModuleVersion, shipped: ModuleVersion):
  def message: Conflict.MessageResult =
    Resolver.Problem(
      s"$plugin needs $key ${wanted.value}, but this Histmersion ships ${shipped.value} — " +
        s"build the plugin against ${shipped.value} (the histmersion-plugin-platform POM), or install a Histmersion that ships ${wanted.value}"
    )

object Conflict:
  type MessageResult = Resolver.Problem
  val MessageResult: Resolver.Problem.type = Resolver.Problem

  type Plugin = Resolver.FileName
  val Plugin: Resolver.FileName.type = Resolver.FileName
