package com.histmersion.plugin.testkit.internal

import com.histmersion.plugin.*
import com.histmersion.plugin.testkit.LogLine
import com.histmersion.pluginhost.HostLog

/** Every line logged while a harness ran: the plugin's own (through `Plugin.Context.log`), and the host's (the
  * acquisition chain, the polite clients). Guarded by `this`: a fetcher may log from several threads.
  */
final private[testkit] class Lines:
  private val buffer = List.newBuilder[LogLine]
  def all: List[LogLine] = synchronized(buffer.result())
  private def add(l: LogLine): Unit = synchronized(buffer += l)
  val plugin: (com.histmersion.util.plugins.Plugin.Id, com.histmersion.plugin.testkit.internal.Lines.PluginArg2, com.histmersion.plugin.testkit.internal.Lines.PluginArg3) => Unit = (id, warn, message) =>
    add(LogLine(if id.isEmpty then "plugins" else id, warn, message))
  val host: HostLog = new HostLog:
    def info(line: com.histmersion.plugin.testkit.internal.Lines.Line) = add(LogLine("host", false, line))
    def warn(line: com.histmersion.plugin.testkit.internal.Lines.Line) = add(LogLine("host", true, line))

object Lines:
  type Line = Line.T
  object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginArg2 = PluginArg2.T
  object PluginArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginArg3 = PluginArg3.T
  object PluginArg3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
