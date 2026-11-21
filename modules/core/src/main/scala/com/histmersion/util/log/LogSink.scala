package com.histmersion
package util.log

import com.histmersion.api.log.LogEvent

trait LogSink:
  def record(scope: TraceScope, subjects: Vector[LogEvent.Subject], what: LogEvent.What): Unit

object LogSink:
  val none: LogSink = new LogSink:
    def record(scope: TraceScope, subjects: Vector[LogEvent.Subject], what: LogEvent.What): Unit = ()
