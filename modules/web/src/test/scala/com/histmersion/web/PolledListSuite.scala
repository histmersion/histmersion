package com.histmersion.web

import com.histmersion.core.*
import com.histmersion.core.Agents.RunState

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Promise}

/** [[PolledList]] (#1798 item 6) — the one controller of the jobs, runs and approvals windows — with a
  * fake server (promises completed by hand) and a fake clock: a poll keeps the pages loaded, a gone
  * cursor restarts, a closed window drops a late answer, an older answer never overwrites a newer one;
  * and the manager window's events keep their older pages on a poll. */
class PolledListSuite extends munit.FunSuite:

  private given ExecutionContext = ExecutionContext.parasitic

  private def job(id: String, state: JobState = JobState.Done) = JobInfo(id, Ingest.KindIngest, state.wire)
  private def page(total: Int, next: Option[String], counts: (String, Int)*) = Paging.Page(total, next, counts.toList)

  /** A fake server: each request waits for the test to answer it; a fake clock holds the next poll. */
  private final class Fixture(liveRows: Boolean):
    val asked = mutable.ListBuffer.empty[(Option[String], Option[String], Promise[(List[JobInfo], Paging.Page)])]
    val timers = mutable.ListBuffer.empty[(Int, () => Unit)]
    val list = PolledList[JobInfo](
      fetch = (upTo, before) => { val p = Promise[(List[JobInfo], Paging.Page)](); asked += ((upTo, before, p)); p.future },
      id = _.id,
      live = (_, _) => liveRows,
      later = (ms, f) => { timers += ((ms, f)); timers.size },
      cancel = _ => ())
    def answer(i: Int, rows: List[JobInfo], pg: Paging.Page): Unit = asked(i)._3.success((rows, pg))
    def fail(i: Int, msg: String): Unit = asked(i)._3.failure(RuntimeException(msg))

  test("open: the newest page; with something live, a poll is scheduled at the quick delay"):
    val f = Fixture(liveRows = true)
    f.list.open()
    assertEquals(f.asked.map(a => (a._1, a._2)).toList, List((None, None)))
    f.answer(0, List(job("b"), job("a")), page(2, None))
    assertEquals(f.list.listing.now().rows.map(_.id), List("b", "a"))
    assert(f.list.loaded.now())
    assertEquals(f.timers.map(_._1).toList, List(Poll.FirstMs))

  test("nothing live (approvals): no poll is scheduled"):
    val f = Fixture(liveRows = false)
    f.list.open()
    f.answer(0, List(job("a")), page(1, None))
    assert(f.timers.isEmpty)

  test("a poll after Show older asks down to the last row shown and keeps the older pages"):
    val f = Fixture(liveRows = true)
    f.list.open()
    f.answer(0, List(job("c"), job("b")), page(4, Some("b")))
    f.list.showOlder()
    assertEquals((f.asked(1)._1, f.asked(1)._2), (None, Some("b")))
    f.answer(1, List(job("a")), page(4, None))
    assertEquals(f.list.listing.now().rows.map(_.id), List("c", "b", "a"))
    f.list.refresh()
    assertEquals(f.asked(2)._1, Some("a"), "the poll reaches the last row shown")
    f.answer(2, List(job("d"), job("c"), job("b"), job("a")), page(4, None))
    assertEquals(f.list.listing.now().rows.map(_.id), List("d", "c", "b", "a"))

  test("the last row shown is gone: the poll starts over from the newest page"):
    val f = Fixture(liveRows = true)
    f.list.open()
    f.answer(0, List(job("c"), job("b")), page(4, Some("b")))
    f.list.showOlder(); f.answer(1, List(job("a")), page(4, None))
    f.list.refresh()
    f.fail(2, "no row 'a'")
    assertEquals((f.asked(3)._1, f.asked(3)._2), (None, None))
    assertEquals(f.list.failure.now(), None, "a restart is not an error")

  test("a failure without a cursor is shown and retried at the slowest pace"):
    val f = Fixture(liveRows = true)
    f.list.open()
    f.fail(0, "HTTP 502")
    assertEquals(f.list.failure.now(), Some("HTTP 502"))
    assertEquals(f.timers.map(_._1).toList, List(Poll.MaxMs))

  test("a closed window drops a late answer; an older answer never overwrites a newer one"):
    val f = Fixture(liveRows = true)
    f.list.open()
    f.list.close()
    f.answer(0, List(job("late")), page(1, None))
    assert(f.list.listing.now().rows.isEmpty && f.timers.isEmpty)
    f.list.open()
    f.list.restart()
    f.answer(2, List(job("new")), page(1, None))
    f.answer(1, List(job("old")), page(1, None))
    assertEquals(f.list.listing.now().rows.map(_.id), List("new"))

  test("the shared schedule: quick on a change, slower while nothing changes, none when nothing is live"):
    assertEquals(Poll.next(None, live = false, changed = true), None)
    assertEquals(Poll.next(Some(1000), live = true, changed = false), Some(1600))
    assertEquals(Poll.next(Some(14000), live = true, changed = false), Some(Poll.MaxMs))
    assertEquals(Poll.next(Some(8000), live = true, changed = true), Some(Poll.FirstMs))

  test("manager events: a poll's newest page keeps the older pages loaded below it"):
    val loaded = List(9L, 8L, 7L, 6L, 5L, 4L)
    assertEquals(ListPaging.keepOlder(loaded, List(11L, 10L, 9L), (a: Long, b: Long) => a < b), List(11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L))
    assertEquals(ListPaging.keepOlder(loaded, Nil, (a: Long, b: Long) => a < b), Nil)

  test("runs: the state a person reads, not the wire word"):
    assertEquals(RunsView.stateLabel(RunState.WaitingApproval), "waiting for approval")
    assertEquals(RunsView.stateLabel(RunState.Running), "running")
    assertEquals(RunsView.statusLabel("waiting"), "waiting for approval")
    assertEquals(RunsView.statusLabel("nonsense"), "nonsense")
