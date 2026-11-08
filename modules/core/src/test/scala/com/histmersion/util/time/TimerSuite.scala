package com.histmersion
package util.time

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
