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

  /** Run `f` inside this scope on the calling thread, restoring the scope it replaced afterwards. */
  def within[A](f: => A): A =
    import TraceScope.thread
    val before = thread.get()
    thread.set(this)
    try f
    finally thread.set(before)

/** The trace scope of the calling thread. The steps that are logged sit deep in shared code (a connection taken for a
  * write, a provider's transport, the problem log) that every operation reaches, so the scope is found on the thread,
  * as the generation pin is: the HTTP layer opens one per request, and work handed to another thread takes its scope
  * along ([[carry]]; the server's pinning does it for every job and run).
  */
object TraceScope:
  private val thread = ThreadLocal[TraceScope | Null]()

  /** The calling thread's scope, if it runs inside one. */
  def current: Option[TraceScope] = Option(thread.get())

  /** The calling thread's trace, if it runs inside a scope. */
  def trace: Option[LogEvent.Trace] = current.map(_.trace)

  /** Record `what` in the calling thread's scope; nothing outside one. */
  def record(what: LogEvent.What, subjects: Vector[LogEvent.Subject] = Vector.empty): Unit =
    current.foreach(_.record(subjects, what))

  /** `f`, to run later on another thread inside the scope the calling thread has now (none: as it is). */
  def carry[A](f: => A): () => A =
    current match
      case Some(s) => () => s.within(f)
      case None    => () => f

  /** A job or run picks its own trace back up (the one its row keeps, `jobs.trace_id`) when it runs: after a restart, a
    * pause or a hand-off, its steps join the trace it was created under, not the one that resumed it. `f` runs under
    * `kept`, as a worker, recording where the calling thread's scope records; as it is when the job keeps no trace or
    * the thread runs in no scope.
    */
  def resumed[A](kept: Option[LogEvent.Trace])(f: => A): A =
    (kept, current) match
      case (Some(trace), Some(scope)) => TraceScope(trace, scope.label, LogEvent.Actor.Worker, scope.sink).within(f)
      case _                          => f
