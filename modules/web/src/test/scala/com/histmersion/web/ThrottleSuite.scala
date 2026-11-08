package com.histmersion
package web

import scala.collection.mutable

class ThrottleSuite extends munit.FunSuite:

  /** A fake clock: `later` queues callbacks, `advance` runs the ones that are due. */
  final private class Clock:
    var t: Double = 0
    private val queue = mutable.ListBuffer.empty[(Double, Throttle.TimerId, () => Unit)]
    private var ids = 0
    def later(ms: Throttle.DelayMs, g: () => Unit): Throttle.TimerId =
      ids += 1
      val id = Throttle.TimerId(ids)
      queue += ((t + ms.value, id, g))
      id
    def cancel(id: Throttle.TimerId): Unit = queue.filterInPlace(_._2 != id)
    def advance(ms: Double): Unit =
      t += ms
      val due = queue.filter(_._1 <= t).toList
      queue --= due
      due.foreach(_._3())

  private def throttled(clock: Clock, out: mutable.ListBuffer[Int]) =
    Throttle[Int](
      Throttle.IntervalMs(100),
      out += _,
      () => ResearchOverview.EpochMillis(clock.t),
      clock.later,
      clock.cancel
    )

  test("the first call runs at once") {
    val c = Clock(); val out = mutable.ListBuffer.empty[Int]
    throttled(c, out)(1)
    assertEquals(out.toList, List(1))
  }

  test("calls inside the window collapse into one trailing run with the latest value") {
    val c = Clock(); val out = mutable.ListBuffer.empty[Int]
    val th = throttled(c, out)
    th(1); c.advance(10); th(2); c.advance(10); th(3)
    assertEquals(out.toList, List(1))
    c.advance(80)
    assertEquals(out.toList, List(1, 3))
  }

  test("a continuous drag keeps producing runs, one per interval") {
    val c = Clock(); val out = mutable.ListBuffer.empty[Int]
    val th = throttled(c, out)
    for i <- 1 to 50 do { th(i); c.advance(10) } // 500 ms of moves every 10 ms
    c.advance(100)
    assert(out.length >= 5 && out.length <= 7, out.toList)
    assertEquals(out.last, 50)
  }

  test("cancelPending drops the trailing run") {
    val c = Clock(); val out = mutable.ListBuffer.empty[Int]
    val th = throttled(c, out)
    th(1); th(2); th.cancelPending(); c.advance(200)
    assertEquals(out.toList, List(1))
  }

  test("FadingLayer.plan splits keys into entering, staying, exiting") {
    val p =
      FadingLayer.plan(Set(FadingLayer.Key("a"), FadingLayer.Key("b")), Set(FadingLayer.Key("b"), FadingLayer.Key("c")))
    assertEquals(
      p,
      FadingLayer.Plan(
        entering = Set(FadingLayer.Key("c")),
        staying = Set(FadingLayer.Key("b")),
        exiting = Set(FadingLayer.Key("a"))
      )
    )
  }

class LatestOnlySuite extends munit.FunSuite:

  /** Runs that complete only when the test says so. */
  final private class Runs:
    val started = mutable.ListBuffer.empty[Int]
    private val done = mutable.Queue.empty[() => Unit]
    val latest = LatestOnly[Int] { (a, d) => started += a; done.enqueue(d) }
    def finish(): Unit = done.dequeue()()

  test("idle: a value runs at once") {
    val r = Runs(); r.latest(1)
    assertEquals(r.started.toList, List(1))
  }

  test("while one runs, only the NEWEST waiting value runs after it") {
    val r = Runs()
    r.latest(1); r.latest(2); r.latest(3); r.latest(4)
    assertEquals(r.started.toList, List(1))
    r.finish()
    assertEquals(r.started.toList, List(1, 4))
    r.finish()
    assertEquals(r.started.toList, List(1, 4), "nothing was waiting")
    r.latest(5)
    assertEquals(r.started.toList, List(1, 4, 5))
  }

  test("drop: the run in flight finishes and nothing follows it") {
    val r = Runs()
    r.latest(1); r.latest(2); r.latest.drop(); r.finish()
    assertEquals(r.started.toList, List(1))
  }

class DebounceSuite extends munit.FunSuite:

  final private class Clock:
    var t: Double = 0
    private val queue = mutable.ListBuffer.empty[(Double, Debounce.TimerId, () => Unit)]
    private var ids = 0
    def later(ms: Debounce.Delay, g: () => Unit): Debounce.TimerId =
      ids += 1
      val id = Debounce.TimerId(ids)
      queue += ((t + ms.value, id, g))
      id
    def cancel(id: Debounce.TimerId): Unit = queue.filterInPlace(_._2 != id)
    def pendingCount: Int = queue.size
    def advance(ms: Double): Unit =
      t += ms
      val due = queue.filter(_._1 <= t).toList
      queue --= due
      due.foreach(_._3())

  test("a burst of calls runs once, after the quiet period, with the latest value") {
    val c = Clock(); val out = mutable.ListBuffer.empty[Int]
    val d = Debounce[Int](Debounce.QuietMs(200), out += _, c.later, c.cancel)
    for i <- 1 to 10 do { d(i); c.advance(50) }
    assertEquals(out.toList, Nil, "nothing while the calls keep coming")
    assertEquals(c.pendingCount, 1, "one timer, not one per call")
    c.advance(200)
    assertEquals(out.toList, List(10))
  }

  test("calls apart by more than the quiet period each run") {
    val c = Clock(); val out = mutable.ListBuffer.empty[Int]
    val d = Debounce[Int](Debounce.QuietMs(100), out += _, c.later, c.cancel)
    d(1); c.advance(150); d(2); c.advance(150)
    assertEquals(out.toList, List(1, 2))
  }

  test("cancelPending drops the run") {
    val c = Clock(); val out = mutable.ListBuffer.empty[Int]
    val d = Debounce[Int](Debounce.QuietMs(100), out += _, c.later, c.cancel)
    d(1); d.cancelPending(); c.advance(500)
    assertEquals(out.toList, Nil)
    assertEquals(c.pendingCount, 0)
  }
