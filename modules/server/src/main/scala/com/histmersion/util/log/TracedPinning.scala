package com.histmersion
package util.log

import com.histmersion.api.log.LogEvent
import com.histmersion.util.jobs.{Carried, Pinning}

final class TracedPinning(pins: Pinning, writer: () => LogWriter) extends Pinning:
  def pinned[A](f: => A): A = pins.pinned(f)

  def carry[A](f: => A): Carried[A] =
    val scope = TraceScope.current.getOrElse {
      val w = writer()
      TraceScope(w.mintTrace(), None, LogEvent.Actor.Worker, w)
    }
    pins.carry(scope.within(f))
