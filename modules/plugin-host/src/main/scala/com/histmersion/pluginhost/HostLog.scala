package com.histmersion.pluginhost

/** Where host code writes a log line: scribe on the server, a buffer in a test. */
trait HostLog:
  def info(line: String): Unit
  def warn(line: String): Unit

object HostLog:
  val scribe: HostLog = new HostLog:
    def info(line: String): Unit = _root_.scribe.info(line)
    def warn(line: String): Unit = _root_.scribe.warn(line)
