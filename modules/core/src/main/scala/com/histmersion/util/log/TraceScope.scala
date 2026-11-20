package com.histmersion
package util.log

import com.histmersion.api.log.LogEvent

/** One unit of traced work: the trace its steps share, the caller's own label, who acts, and where its events go. */
final case class TraceScope(
    trace: LogEvent.Trace,
    label: Option[LogEvent.Label],
    actor: LogEvent.Actor,
    sink: LogSink
):
  def record(subjects: Vector[LogEvent.Subject], what: LogEvent.What): Unit = sink.record(this, subjects, what)
