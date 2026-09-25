package histdb.web

import scala.collection.mutable

class ThrottleSuite extends munit.FunSuite:

  /** A fake clock: `later` queues callbacks, `advance` runs the ones that are due. */
  private final class Clock:
    var t: Double = 0
    private val queue = mutable.ListBuffer.empty[(Double, Int, () => Unit)]
    private var ids = 0
    def later(ms: Double, g: () => Unit): Int =
      ids += 1; queue += ((t + ms, ids, g)); ids
    def cancel(id: Int): Unit = queue.filterInPlace(_._2 != id)
    def advance(ms: Double): Unit =
      t += ms
      val due = queue.filter(_._1 <= t).toList
      queue --= due
      due.foreach(_._3())

  private def throttled(clock: Clock, out: mutable.ListBuffer[Int]) =
    Throttle[Int](100, out += _, () => clock.t, clock.later, clock.cancel)

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
    val p = FadingLayer.plan(Set("a", "b"), Set("b", "c"))
    assertEquals(p, FadingLayer.Plan(entering = Set("c"), staying = Set("b"), exiting = Set("a")))
  }
