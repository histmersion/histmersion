package com.histmersion.pluginhost

/** Where host code writes a log line: scribe on the server, a buffer in a test. */
trait HostLog:
  def info(line: String): Unit
  def warn(line: String): Unit

  /** The same log as the plugin SPI's, for code the host shares with the plugins (the upstreams' clients). */
  def plugin: com.histmersion.plugin.PluginLog =
    val self = this
    new com.histmersion.plugin.PluginLog:
      def info(message: String): Unit = self.info(message)
      def warn(message: String): Unit = self.warn(message)

object HostLog:
  val scribe: HostLog = new HostLog:
    def info(line: String): Unit = _root_.scribe.info(line)
    def warn(line: String): Unit = _root_.scribe.warn(line)
