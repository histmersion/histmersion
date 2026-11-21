package com.histmersion
package util.log

import com.histmersion.api.log.LogEvent

final case class TraceScope(
    trace: LogEvent.Trace,
    label: Option[LogEvent.Label],
    actor: LogEvent.Actor,
    sink: LogSink
):
  def record(subjects: Vector[LogEvent.Subject], what: LogEvent.What): Unit = sink.record(this, subjects, what)

  def within[A](f: => A): A =
    import TraceScope.thread
    val before = thread.get()
    thread.set(this)
    try f
    finally thread.set(before)

object TraceScope:
  private val thread = ThreadLocal[TraceScope | Null]()

  def current: Option[TraceScope] = Option(thread.get())

  def trace: Option[LogEvent.Trace] = current.map(_.trace)

  def record(what: LogEvent.What, subjects: Vector[LogEvent.Subject] = Vector.empty): Unit =
    current.foreach(_.record(subjects, what))

  def carry[A](f: => A): () => A =
    current match
      case Some(s) => () => s.within(f)
      case None    => () => f

  def resumed[A](kept: Option[LogEvent.Trace])(f: => A): A =
    (kept, current) match
      case (Some(trace), Some(scope)) => TraceScope(trace, scope.label, LogEvent.Actor.Worker, scope.sink).within(f)
      case _                          => f
