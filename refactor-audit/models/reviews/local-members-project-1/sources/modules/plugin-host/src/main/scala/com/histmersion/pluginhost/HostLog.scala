package com.histmersion.pluginhost

/** Where host code writes a log line: scribe on the server, a buffer in a test. */
trait HostLog:
  def info(line: com.histmersion.pluginhost.HostLog.InfoLine): Unit
  def warn(line: com.histmersion.pluginhost.HostLog.WarnLine): Unit

  /** The same log as the plugin SPI's, for code the host shares with the plugins (the upstreams' clients). */
  def plugin: com.histmersion.plugin.Plugin.Log =
    val self = this
    new com.histmersion.plugin.Plugin.Log:
      def info(message: com.histmersion.pluginhost.HostLog.PluginInfoMessage): Unit = self.info(message)
      def warn(message: com.histmersion.pluginhost.HostLog.PluginWarnMessage): Unit = self.warn(message)

object HostLog:
  type PluginInfoMessage = PluginInfoMessage.T
  object PluginInfoMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PluginWarnMessage = PluginWarnMessage.T
  object PluginWarnMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type InfoLine = InfoLine.T
  object InfoLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WarnLine = WarnLine.T
  object WarnLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val scribe: HostLog = new HostLog:
    def info(line: com.histmersion.pluginhost.HostLog.InfoLine): Unit = _root_.scribe.info(line)
    def warn(line: com.histmersion.pluginhost.HostLog.WarnLine): Unit = _root_.scribe.warn(line)
