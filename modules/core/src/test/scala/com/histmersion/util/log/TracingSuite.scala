package com.histmersion
package util.log

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.log.LogEvent
import com.histmersion.api.log.LogEvent.{Subject, What}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

class TracingSuite extends munit.FunSuite:
  final private class Recorded extends LogSink:
    val events = ConcurrentLinkedQueue[(LogEvent.Trace, What)]()
    def record(scope: TraceScope, subjects: Vector[Subject], what: What): Unit =
      val _ = events.add((scope.trace, what))

  private val lost = What.Lost(LogEvent.Count(1), LogEvent.Loss.QueueFull)

  test("outside a scope nothing is recorded; inside, the step goes to the scope's sink under its trace"):
    val sink = Recorded()
    Tracing.record(lost)
    val scope = TraceScope(LogEvent.Trace("t1"), None, LogEvent.Actor.Client, sink)
    Tracing.within(scope)(Tracing.record(lost))
    assertEquals(sink.events.asScala.toList.map(_._1), List("t1"))
    assertEquals(Tracing.scope, None)

  test("a nested scope is undone when it ends, also when it throws"):
    val sink = Recorded()
    val outer = TraceScope(LogEvent.Trace("outer"), None, LogEvent.Actor.Client, sink)
    val inner = TraceScope(LogEvent.Trace("inner"), None, LogEvent.Actor.Worker, sink)
    Tracing.within(outer) {
      intercept[IllegalStateException](Tracing.within(inner)(throw IllegalStateException("x")))
      assertEquals(Tracing.trace, Some("outer"))
    }

  test("work carried to another thread runs in the scope the thread that handed it over had"):
    val sink = Recorded()
    val scope = TraceScope(LogEvent.Trace("carried"), None, LogEvent.Actor.Client, sink)
    val work = Tracing.within(scope)(Tracing.carry(Tracing.record(lost)))
    val t = Thread.ofPlatform().start(() => work())
    t.join()
    assertEquals(sink.events.asScala.toList.map(_._1), List("carried"))

  test("a resumed job takes its own trace back, recording where the scope that resumed it records"):
    val sink = Recorded()
    val resumer = TraceScope(LogEvent.Trace("resume-request"), None, LogEvent.Actor.Client, sink)
    Tracing.within(resumer) {
      JobTrace.resumed(Some(LogEvent.Trace("job-own")))(Tracing.record(lost))
      JobTrace.resumed(None)(Tracing.record(lost))
    }
    JobTrace.resumed(Some(LogEvent.Trace("no-scope")))(Tracing.record(lost))
    assertEquals(sink.events.asScala.toList.map(_._1), List("job-own", "resume-request"))
