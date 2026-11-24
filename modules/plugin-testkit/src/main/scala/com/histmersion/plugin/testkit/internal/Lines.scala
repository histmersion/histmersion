package com.histmersion
package plugin.testkit.internal

import com.histmersion.plugin.*
import com.histmersion.plugin.testkit.LogLine
import com.histmersion.pluginhost.{HostLog, HostServices}
import com.histmersion.lint.{LintRule, tolerated}

/** Every line logged while a harness ran: the plugin's own (through `Plugin.Context.log`), and the host's (the
  * acquisition chain, the polite clients). Guarded by `this`: a fetcher may log from several threads.
  */
final private[testkit] class Lines:
  private val buffer = List.newBuilder[LogLine]
  def all: List[LogLine] = synchronized(buffer.result())
  private def add(l: LogLine): Unit = synchronized(buffer += l)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  val plugin: (HostServices.PluginId, Boolean, HostServices.Line) => Unit = (id, warn, message) =>
    add(
      LogLine(
        LogLine.Source(if id.value.isEmpty then "plugins" else id.value),
        warn,
        LogLine.Text(message.value)
      )
    )
  val host: HostLog = new HostLog:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def info(line: HostLog.Line) = add(LogLine(LogLine.Source("host"), false, LogLine.Text(line.value)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    def warn(line: HostLog.Line) = add(LogLine(LogLine.Source("host"), true, LogLine.Text(line.value)))
