package com.histmersion
package pluginhost

import com.histmersion.plugin.Plugin
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** Where host code writes a log line: scribe on the server, a buffer in a test. */
trait HostLog:
  def info(line: HostLog.Line): Unit
  def warn(line: HostLog.Line): Unit

  /** The same log as the plugin SPI's, for code the host shares with the plugins (the upstreams' clients). */
  def plugin: Plugin.Log =
    val self = this
    new Plugin.Log:
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def info(message: Plugin.Log.Line): Unit = self.info(HostLog.Line(message.value))
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def warn(message: Plugin.Log.Line): Unit = self.warn(HostLog.Line(message.value))

object HostLog:
  type Line = Line.T
  object Line extends Newtype[String, Pure]

  val scribe: HostLog = new HostLog:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Root, "pending G47 (no FQN noise)")
    def info(line: Line): Unit = _root_.scribe.info(line.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Root, "pending G47 (no FQN noise)")
    def warn(line: Line): Unit = _root_.scribe.warn(line.value)
