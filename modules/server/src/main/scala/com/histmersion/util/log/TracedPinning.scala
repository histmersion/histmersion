package com.histmersion
package util.log

import com.histmersion.api.log.LogEvent
import com.histmersion.util.jobs.{Carried, Pinning}

/** `pins`, also carrying the calling thread's trace scope to the thread work is handed to: a job or run started by a
  * request records its steps under that request's trace. Work handed over from no scope (the server's own: a scheduler
  * tick, a start-up) gets a scope of its own, with a new trace, recording into `writer`, so its steps are still linked
  * and a resumed job can take its own trace back ([[JobTrace]]).
  */
final class TracedPinning(pins: Pinning, writer: () => LogWriter) extends Pinning:
  def pinned[A](f: => A): A = pins.pinned(f)

  def carry[A](f: => A): Carried[A] =
    val scope = Tracing.scope.getOrElse {
      val w = writer()
      TraceScope(w.mintTrace(), None, LogEvent.Actor.Worker, w)
    }
    pins.carry(Tracing.within(scope)(f))
