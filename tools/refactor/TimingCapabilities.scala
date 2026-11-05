import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class TimingCapabilities extends SyntacticRule("TimingCapabilities") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    val sources = List(
      "modules/core/src/main/scala/com/histmersion/util/time/Timer.scala" -> """package com.histmersion.util.time

import scala.concurrent.duration.FiniteDuration

trait Timer:
  def start(): Timer.Measure

  final def after(duration: FiniteDuration): Timer.Deadline =
    val measure = start()
    new Timer.Deadline:
      def reached: Boolean = measure.elapsed >= duration
      def exceeded: Boolean = measure.elapsed > duration

object Timer:
  trait Measure:
    def elapsed: FiniteDuration

  trait Deadline:
    def reached: Boolean
    def exceeded: Boolean
""",
      "modules/core/src/main/scala/com/histmersion/util/time/Pause.scala" -> """package com.histmersion.util.time

import scala.concurrent.duration.FiniteDuration

trait Pause:
  def sleep(duration: FiniteDuration): Unit
""",
      "modules/server/src/main/scala/com/histmersion/util/time/SystemTimer.scala" -> """package com.histmersion.util.time

import scala.concurrent.duration.{FiniteDuration, NANOSECONDS}

final class SystemTimer extends Timer:
  def start(): Timer.Measure =
    val started = System.nanoTime()
    new Timer.Measure:
      def elapsed: FiniteDuration = FiniteDuration(System.nanoTime() - started, NANOSECONDS)
""",
      "modules/server/src/main/scala/com/histmersion/util/time/ThreadPause.scala" -> """package com.histmersion.util.time

import scala.concurrent.duration.FiniteDuration

final class ThreadPause extends Pause:
  def sleep(duration: FiniteDuration): Unit =
    java.util.concurrent.TimeUnit.NANOSECONDS.sleep(duration.toNanos)
""",
      "modules/core/src/test/scala/com/histmersion/util/time/TimerSuite.scala" -> """package com.histmersion.util.time

import scala.concurrent.duration.*

class TimerSuite extends munit.FunSuite:
  test("deadline distinguishes equality from passing the deadline"):
    var elapsed = 0.nanos
    val timer = new Timer:
      def start(): Timer.Measure = new Timer.Measure:
        def elapsed: FiniteDuration = read()
      private def read(): FiniteDuration = elapsed
    val deadline = timer.after(5.millis)
    assert(!deadline.reached)
    assert(!deadline.exceeded)
    elapsed = 5.millis
    assert(deadline.reached)
    assert(!deadline.exceeded)
    elapsed = 5000001.nanos
    assert(deadline.reached)
    assert(deadline.exceeded)

  test("each measurement starts when requested"):
    var now = 0.nanos
    val timer = new Timer:
      def start(): Timer.Measure =
        val began = now
        new Timer.Measure:
          def elapsed: FiniteDuration = now - began
    val first = timer.start()
    now = 3.millis
    val second = timer.start()
    now = 8.millis
    assertEquals(first.elapsed, 8.millis)
    assertEquals(second.elapsed, 5.millis)

  test("a zero budget is reached immediately but is not exceeded"):
    val timer = new Timer:
      def start(): Timer.Measure = new Timer.Measure:
        def elapsed: FiniteDuration = Duration.Zero
    val deadline = timer.after(Duration.Zero)
    assert(deadline.reached)
    assert(!deadline.exceeded)
"""
    )
    sources.foreach { case(p,s) => require(!Files.exists(Paths.get(p))); s.parse[Source].get }
    val report = Paths.get("refactor-audit/monotonic/capabilities"); require(!Files.exists(report))
    Files.createDirectories(report)
    sources.foreach { case(p,s) =>
      val path = Paths.get(p); Files.createDirectories(path.getParent); Files.writeString(path,s)
      val evidence = report.resolve(path); Files.createDirectories(evidence.getParent); Files.writeString(evidence,s)
    }
    Files.write(report.resolve("status.txt"),List("CREATED_BY_SCALAFIX", "Five parsed source files created; adapters are not wired and no migration fixes are claimed.", "Timer is an abstract core mechanism; real adapters are server-only; no global singleton or default.").asJava)
    println("Created Timer/Pause contracts, explicit server adapters and three deterministic timer tests")
  }
}
