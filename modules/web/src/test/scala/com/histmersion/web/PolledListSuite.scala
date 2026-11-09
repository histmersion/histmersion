package com.histmersion
package web

import com.histmersion.api.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.Paging
import com.histmersion.api.automation.Agents.RunState

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Promise}
import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.api.library.Ingest
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.automation.Agents

/** [[PolledList]] (#1798 item 6) — the one controller of the jobs, runs and approvals windows — with a fake server
  * (promises completed by hand) and a fake clock: a poll keeps the pages loaded, a gone cursor restarts, a closed
  * window drops a late answer, an older answer never overwrites a newer one; and the manager window's events keep their
  * older pages on a poll.
  */
class PolledListSuite extends munit.FunSuite:

  private given ExecutionContext = ExecutionContext.parasitic

  private def job(id: SpatialHierarchyResult.Job.Id, state: JobState = JobState.Done) =
    JobInfo(id, JobKinds.Wire(Ingest.KindIngest), state.wire)
  private def jobId(id: String) = SpatialHierarchyResult.Job.Id(id)
  private def page(
      total: Paging.Page.Total,
      next: Option[Paging.Page.Next],
      counts: (Paging.Page.CountState, Paging.Count)*
  ) = Paging.Page(total, next, counts.toList)

  /** A fake server: each request waits for the test to answer it; a fake clock holds the next poll. */
  final private class Fixture(liveRows: Boolean):
    val asked =
      mutable.ListBuffer.empty[(Option[Paging.Cursor], Option[Paging.Cursor], Promise[(List[JobInfo], Paging.Page)])]
    val timers = mutable.ListBuffer.empty[(Poll.Revision, () => Unit)]
    val list = PolledList[JobInfo](
      fetch =
        (upTo, before) => { val p = Promise[(List[JobInfo], Paging.Page)](); asked += ((upTo, before, p)); p.future },
      id = j => PolledList.ItemId(j.id.value),
      live = (_, _) => liveRows,
      later = (ms, f) => { timers += ((ms, f)); PolledList.TimerId(timers.size) },
      cancel = _ => ()
    )
    def answer(i: Int, rows: List[JobInfo], pg: Paging.Page): Unit = asked(i)._3.success((rows, pg))
    def fail(i: Int, msg: String): Unit = asked(i)._3.failure(RuntimeException(msg))

  test("open: the newest page; with something live, a poll is scheduled at the quick delay"):
    val f = Fixture(liveRows = true)
    f.list.open()
    assertEquals(f.asked.map(a => (a._1, a._2)).toList, List((None, None)))
    f.answer(0, List(job(jobId("b")), job(jobId("a"))), page(Paging.Page.Total(2), None))
    assertEquals(f.list.listing.now().rows.map(_.id), List("b", "a").map(SpatialHierarchyResult.Job.Id.apply))
    assert(f.list.loaded.now().value)
    assertEquals(f.timers.map(_._1).toList, List(Poll.FirstMs))

  test("nothing live (approvals): no poll is scheduled"):
    val f = Fixture(liveRows = false)
    f.list.open()
    f.answer(0, List(job(jobId("a"))), page(Paging.Page.Total(1), None))
    assert(f.timers.isEmpty)

  test("a poll after Show older asks down to the last row shown and keeps the older pages"):
    val f = Fixture(liveRows = true)
    f.list.open()
    f.answer(0, List(job(jobId("c")), job(jobId("b"))), page(Paging.Page.Total(4), Some(Paging.Page.Next("b"))))
    f.list.showOlder()
    assertEquals((f.asked(1)._1, f.asked(1)._2), (None, Some("b")))
    f.answer(1, List(job(jobId("a"))), page(Paging.Page.Total(4), None))
    assertEquals(f.list.listing.now().rows.map(_.id), List("c", "b", "a").map(SpatialHierarchyResult.Job.Id.apply))
    f.list.refresh()
    assertEquals(f.asked(2)._1, Some("a"), "the poll reaches the last row shown")
    f.answer(
      2,
      List(job(jobId("d")), job(jobId("c")), job(jobId("b")), job(jobId("a"))),
      page(Paging.Page.Total(4), None)
    )
    assertEquals(f.list.listing.now().rows.map(_.id), List("d", "c", "b", "a").map(SpatialHierarchyResult.Job.Id.apply))

  test("the last row shown is gone: the poll starts over from the newest page"):
    val f = Fixture(liveRows = true)
    f.list.open()
    f.answer(0, List(job(jobId("c")), job(jobId("b"))), page(Paging.Page.Total(4), Some(Paging.Page.Next("b"))))
    f.list.showOlder(); f.answer(1, List(job(jobId("a"))), page(Paging.Page.Total(4), None))
    f.list.refresh()
    f.fail(2, "no row 'a'")
    assertEquals((f.asked(3)._1, f.asked(3)._2), (None, None))
    assertEquals(f.list.failure.now(), None, "a restart is not an error")

  test("a failure without a cursor is shown and retried at the slowest pace"):
    val f = Fixture(liveRows = true)
    f.list.open()
    f.fail(0, "HTTP 502")
    assertEquals(f.list.failure.now().map(_.sentence), Some("HTTP 502"))
    assertEquals(f.timers.map(_._1).toList, List(Poll.MaxMs))

  test("a closed window drops a late answer; an older answer never overwrites a newer one"):
    val f = Fixture(liveRows = true)
    f.list.open()
    f.list.close()
    f.answer(0, List(job(jobId("late"))), page(Paging.Page.Total(1), None))
    assert(f.list.listing.now().rows.isEmpty && f.timers.isEmpty)
    f.list.open()
    f.list.restart()
    f.answer(2, List(job(jobId("new"))), page(Paging.Page.Total(1), None))
    f.answer(1, List(job(jobId("old"))), page(Paging.Page.Total(1), None))
    assertEquals(f.list.listing.now().rows.map(_.id), List("new").map(SpatialHierarchyResult.Job.Id.apply))

  /** One polled item (the agent run's window), on the same fake server and clock. */
  final private class Single:
    val asked = mutable.ListBuffer.empty[Promise[JobInfo]]
    val timers = mutable.ListBuffer.empty[(Poll.Revision, () => Unit)]
    val settled = mutable.ListBuffer.empty[(Option[JobInfo.Status], JobInfo.Status)]
    val one = PolledList.single[JobInfo](
      fetch = () => { val p = Promise[JobInfo](); asked += p; p.future },
      live = j => !j.state.isTerminal,
      after = (before, now) => settled += ((before.map(_.status), now.status)),
      later = (ms, f) => { timers += ((ms, f)); PolledList.TimerId(timers.size) },
      cancel = _ => ()
    )

  test("single (#1946): the item, polled while live, then backing off; none once it settles"):
    val f = Single()
    f.one.open()
    f.asked(0).success(job(jobId("r"), JobState.Running))
    assertEquals(f.one.listing.now().rows.map(_.status), List(JobState.Running.wire))
    assertEquals(f.timers.map(_._1).toList, List(Poll.FirstMs))
    assert(f.settled.isEmpty, "the first load is not a poll")
    f.timers.last._2()
    f.asked(1).success(job(jobId("r"), JobState.Running))
    assertEquals(f.timers.map(_._1).toList, List(Poll.FirstMs, 1600), "unchanged: slower")
    f.timers.last._2()
    f.asked(2).success(job(jobId("r"), JobState.Done))
    assertEquals(f.timers.size, 2, "settled: no further poll")
    assertEquals(f.settled.toList.last, (Some(JobState.Running.wire), JobState.Done.wire))
    assertEquals(f.one.listing.now().hasOlder, false)

  test("single (#1946): a closed window drops a late answer; a failure is shown and retried slowly"):
    val f = Single()
    f.one.open()
    f.one.close()
    f.asked(0).success(job(jobId("late"), JobState.Running))
    assert(f.one.listing.now().rows.isEmpty && f.timers.isEmpty)
    f.one.open()
    f.asked(1).failure(RuntimeException("HTTP 404"))
    assertEquals(f.one.failure.now().map(_.sentence), Some("HTTP 404"))
    assertEquals(f.timers.map(_._1).toList, List(Poll.MaxMs))
    f.one.refresh(changed = Poll.DataChanged(true)) // a control button was pressed
    f.asked(2).success(job(jobId("r"), JobState.Running))
    assertEquals(f.one.failure.now(), None)
    assertEquals(f.timers.last._1, Poll.FirstMs)

  test("the shared schedule: quick on a change, slower while nothing changes, none when nothing is live"):
    assertEquals(Poll.next(None, live = Poll.LiveMode(false), changed = Poll.DataChanged(true)), None)
    assertEquals(
      Poll.next(Some(Poll.Revision(1000)), live = Poll.LiveMode(true), changed = Poll.DataChanged(false)),
      Some(1600)
    )
    assertEquals(
      Poll.next(Some(Poll.Revision(14000)), live = Poll.LiveMode(true), changed = Poll.DataChanged(false)),
      Some(Poll.MaxMs)
    )
    assertEquals(
      Poll.next(Some(Poll.Revision(8000)), live = Poll.LiveMode(true), changed = Poll.DataChanged(true)),
      Some(Poll.FirstMs)
    )

  test("manager events: a poll's newest page keeps the older pages loaded below it"):
    val loaded = List(9L, 8L, 7L, 6L, 5L, 4L)
    assertEquals(
      ListPaging.keepOlder(loaded, List(11L, 10L, 9L), (a: Long, b: Long) => a < b),
      List(11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L)
    )
    assertEquals(ListPaging.keepOlder(loaded, Nil, (a: Long, b: Long) => a < b), Nil)

  test("runs: the state a person reads, not the wire word"):
    assertEquals(RunsView.stateLabel(RunState.WaitingApproval), "Waiting for a decision")
    assertEquals(RunsView.stateLabel(RunState.Running), "Running")
    assertEquals(RunsView.statusLabel(Agents.RunInfo.Status("waiting")), "Waiting for a decision")
    assertEquals(RunsView.statusLabel(Agents.RunInfo.Status("nonsense")), "Nonsense")
