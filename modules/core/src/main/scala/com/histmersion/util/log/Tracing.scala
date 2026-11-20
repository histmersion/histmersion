package com.histmersion
package util.log

import com.histmersion.api.log.LogEvent

/** The trace scope of the calling thread. The steps that are logged sit deep in shared code (a connection taken for a
  * write, a provider's transport, the problem log) that every operation reaches, so the scope is found on the thread,
  * as the generation pin is: the HTTP layer opens one per request, and work handed to another thread takes its scope
  * along ([[carry]]; the server's pinning does it for every job and run).
  */
object Tracing:
  private val current = ThreadLocal[TraceScope | Null]()

  /** The calling thread's scope, if it runs inside one. */
  def scope: Option[TraceScope] = Option(current.get())

  /** The calling thread's trace, if it runs inside a scope. */
  def trace: Option[LogEvent.Trace] = scope.map(_.trace)

  /** Run `f` inside `s`, restoring the scope it replaced afterwards. */
  def within[A](s: TraceScope)(f: => A): A =
    val before = current.get()
    current.set(s)
    try f
    finally current.set(before)

  /** Record `what` in the calling thread's scope; nothing outside one. */
  def record(what: LogEvent.What, subjects: Vector[LogEvent.Subject] = Vector.empty): Unit =
    scope.foreach(_.record(subjects, what))

  /** `f`, to run later on another thread inside the scope the calling thread has now (none: as it is). */
  def carry[A](f: => A): () => A =
    scope match
      case Some(s) => () => within(s)(f)
      case None    => () => f
