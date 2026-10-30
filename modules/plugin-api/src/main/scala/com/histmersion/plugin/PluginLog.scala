package com.histmersion.plugin

import scala.compiletime.deferred

/** The host's log, with the plugin's id on every line. */
trait PluginLog:
  def info(message: String): Unit
  def warn(message: String): Unit
