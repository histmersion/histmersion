package com.histmersion
package util.log

import com.histmersion.api.log.LogEvent

/** A job or run picks its own trace back up (the one its row keeps, `jobs.trace_id`) when it runs: after a restart, a
  * pause or a hand-off, its steps join the trace it was created under, not the one that resumed it.
  */
object JobTrace:
  /** Run `f` under `kept` (the job's own trace), as a worker, recording where the calling thread's scope records; as it
    * is when the job keeps no trace or the thread runs in no scope.
    */
  def resumed[A](kept: Option[LogEvent.Trace])(f: => A): A =
    (kept, Tracing.scope) match
      case (Some(trace), Some(scope)) =>
        Tracing.within(TraceScope(trace, scope.label, LogEvent.Actor.Worker, scope.sink))(f)
      case _ => f
