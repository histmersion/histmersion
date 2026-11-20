package com.histmersion
package util.log

import com.histmersion.api.log.LogEvent

/** Where the structured log's events go: the server's one writer, which mints each event's id and time. Recording never
  * blocks and never fails the step it describes.
  */
trait LogSink:
  def record(scope: TraceScope, subjects: Vector[LogEvent.Subject], what: LogEvent.What): Unit

object LogSink:
  /** Records nothing: code running outside a server (tests, tools). */
  val none: LogSink = new LogSink:
    def record(scope: TraceScope, subjects: Vector[LogEvent.Subject], what: LogEvent.What): Unit = ()
