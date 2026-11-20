package com.histmersion
package util.log

import com.histmersion.util.jobs.{Carried, Pinning}

/** `pins`, also carrying the calling thread's trace scope to the thread work is handed to: a job or run started by a
  * request records its steps under that request's trace.
  */
final class TracedPinning(pins: Pinning) extends Pinning:
  def pinned[A](f: => A): A = pins.pinned(f)

  def carry[A](f: => A): Carried[A] =
    val traced = Tracing.carry(f)
    pins.carry(traced())
