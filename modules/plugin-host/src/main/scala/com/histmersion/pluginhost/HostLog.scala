package com.histmersion.pluginhost

/** Where host code writes a log line: scribe on the server, a buffer in a test. */
trait HostLog:
  def info(line: com.histmersion.pluginhost.HostLog.Line): Unit
  def warn(line: com.histmersion.pluginhost.HostLog.Line): Unit

  /** The same log as the plugin SPI's, for code the host shares with the plugins (the upstreams' clients). */
  def plugin: com.histmersion.plugin.Plugin.Log =
    val self = this
    new com.histmersion.plugin.Plugin.Log:
      def info(message: com.histmersion.plugin.Plugin.Log.InfoMessage): Unit = self.info(HostLog.Line(message.value))
      def warn(message: com.histmersion.plugin.Plugin.Log.WarnMessage): Unit = self.warn(HostLog.Line(message.value))

object HostLog:
  type Line = Line.T
  object Line extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  val scribe: HostLog = new HostLog:
    def info(line: com.histmersion.pluginhost.HostLog.Line): Unit = _root_.scribe.info(line.value)
    def warn(line: com.histmersion.pluginhost.HostLog.Line): Unit = _root_.scribe.warn(line.value)
