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
  val plugin: (String, Boolean, String) => Unit = (id, warn, message) =>
    add(LogLine(if id.isEmpty then "plugins" else id, warn, message))
  val host: HostLog = new HostLog:
    def info(line: String) = add(LogLine("host", false, line))
    def warn(line: String) = add(LogLine("host", true, line))
