package com.histmersion
package plugin.testkit.internal

import com.histmersion.plugin.*
import com.histmersion.plugin.testkit.LogLine
import com.histmersion.pluginhost.{HostLog, HostServices}

/** Every line logged while a harness ran: the plugin's own (through `Plugin.Context.log`), and the host's (the
  * acquisition chain, the polite clients). Guarded by `this`: a fetcher may log from several threads.
  */
final private[testkit] class Lines:
  private val buffer = List.newBuilder[LogLine]
  def all: List[LogLine] = synchronized(buffer.result())
  private def add(l: LogLine): Unit = synchronized(buffer += l)
  val plugin: (HostServices.LogArg1, HostServices.LogArg2, HostServices.LogArg3) => Unit = (id, warn, message) =>
    add(LogLine(LogLine.Source(if id.value.isEmpty then "plugins" else id.value), warn.value, LogLine.Message(message.value)))
  val host: HostLog = new HostLog:
    def info(line: HostLog.Line) = add(LogLine(LogLine.Source("host"), false, LogLine.Message(line.value)))
    def warn(line: HostLog.Line) = add(LogLine(LogLine.Source("host"), true, LogLine.Message(line.value)))
