package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.database.Transactor

import com.histmersion.domain.automation.approvals.ApprovalQuery

import com.histmersion.util.jobs.JobRepo

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.{ApprovalState, RunState}

import com.augustnagro.magnum.*
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.util.database.Migrate
import com.histmersion.api.automation.Agents
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig
import com.histmersion.api.jobs.JobState
import com.histmersion.api.automation.Run
import com.histmersion.domain.automation.Goal

/** Keyset paging of the jobs, agent-runs and approvals lists (core `Paging`, V086) against a real Postgres
  * (HISTMERSION_TEST_DB_URL; skipped otherwise). Other suites write jobs and approvals at the same time, so each
  * fixture is kept apart: its jobs carry a status of their own, its runs a goal of their own, its approvals sit in
  * 1971, older than anything another suite writes.
  */
class PagingRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private val tag = s"zzpg${System.nanoTime().toHexString.takeRight(8)}"

  private def pageStatus(st: String) = Some(JobState.Wire(st))
  private def jobCursor(id: String) = Some(JobRepo.PageCursor(id))
  private def runCursor(id: String) = Some(AgentRepo.PageCursor(id))
  private def runWire(w: Run.State.Wire) =
    domain.automation.Run.State.Wire(w.value)

  /** `n` jobs of `status`/`kind`: two share each second (ties broken by id), newest = the highest index. */
  private def jobs(
      prefix: String,
      status: String,
      n: Int,
      kind: String = "ingest",
      goal: Option[String] = None
  ): List[String] =
    val ids = (1 to n).map(i => f"$prefix-$i%03d").toList
    transact(ds):
      ids.zipWithIndex.foreach { (id, i) =>
        val at = f"2001-01-01T00:00:${i / 2}%02dZ"
        sql"""INSERT INTO jobs (id, kind, status, created_at, goal_slug)
              VALUES ($id, $kind, $status, $at::timestamptz, $goal)""".update.run()
      }
    ids.reverse // newest first

  override def afterSuite(): Unit =
    if testDb.nonEmpty then
      transact(ds):
        sql"DELETE FROM approvals WHERE citekey LIKE ${tag + "%"}".update.run()
        sql"DELETE FROM jobs WHERE id LIKE ${tag + "%"}".update.run()
      ()

  test("jobs: newest first, ties broken by id, the cursor continues exactly, the counts are the table's"):
    val st = s"${tag}j"
    val all = jobs(s"$tag-j", st, 7)
    val (p1, more1) = Transactor(ds).read(JobRepo.page(pageStatus(st), PageSize(3))).toOption.get
    assertEquals(p1.map(_.id).toList, all.take(3))
    assert(more1)
    val (p2, more2) =
      Transactor(ds).read(JobRepo.page(pageStatus(st), PageSize(3), before = jobCursor(p1.last.id.value))).toOption.get
    assertEquals(p2.map(_.id).toList, all.slice(3, 6))
    assert(more2)
    val (p3, more3) =
      Transactor(ds).read(JobRepo.page(pageStatus(st), PageSize(3), before = jobCursor(p2.last.id.value))).toOption.get
    assertEquals(p3.map(_.id).toList, all.drop(6))
    assert(!more3, "the last page says nothing is older")
    // ties: -006 and -007 share their second; -007 (the higher id) comes first
    assertEquals(all.take(2), List(s"$tag-j-007", s"$tag-j-006"))
    assertEquals(Transactor(ds).read(JobRepo.stateCounts).find(_._1.value == st), Some(st -> 7))
    // the old list reads the same order
    assertEquals(Transactor(ds).read(JobRepo.list(Some(JobState.Wire(st)), PageSize(7))).map(_.id).toList, all)

  test("jobs: upTo is every row down to that one; a filter narrows the page, not the cursor's place"):
    val st = s"${tag}u"
    val all = jobs(s"$tag-u", st, 6)
    val (rows, more) =
      Transactor(ds).read(JobRepo.page(pageStatus(st), PageSize(10), upTo = jobCursor(all(3)))).toOption.get
    assertEquals(rows.map(_.id).toList, all.take(4))
    assert(more, "rows older than the upTo row exist")
    val (whole, none) =
      Transactor(ds).read(JobRepo.page(pageStatus(st), PageSize(10), upTo = jobCursor(all.last))).toOption.get
    assertEquals((whole.size, none), (6, false))
    // more rows down to the upTo row than the limit: the newest `limit`, and more
    val (cut, cutMore) =
      Transactor(ds).read(JobRepo.page(pageStatus(st), PageSize(2), upTo = jobCursor(all.last))).toOption.get
    assertEquals((cut.map(_.id).toList, cutMore), (all.take(2), true))
    // a cursor from another state still marks a place: the page is this state's rows older than it
    val other = s"$tag-o-1"
    transact(ds)(sql"""INSERT INTO jobs (id, kind, status, created_at)
                       VALUES ($other, 'ingest', ${s"${tag}o"}, '2001-01-01T00:00:01.5Z'::timestamptz)""".update.run())
    assertEquals(
      Transactor(ds)
        .read(JobRepo.page(pageStatus(st), PageSize(10), before = jobCursor(other)))
        .toOption
        .get
        ._1
        .map(_.id)
        .toList,
      all.drop(2)
    )
    assert(
      Transactor(ds).read(JobRepo.page(pageStatus(st), PageSize(10), before = jobCursor(s"$tag-nope"))).isLeft,
      "a cursor naming no job"
    )

  test("agent runs: one goal's runs page by keyset, and the counts are that goal's, per state"):
    val goal = s"${tag}g"
    val goalSlug = Goal.Slug.assume(goal)
    val done = jobs(s"$tag-r", RunState.Done.wire.value, 5, Agents.JobKind, Some(goal))
    val paused = jobs(s"$tag-s", RunState.Paused.wire.value, 1, Agents.JobKind, Some(goal))
    val (p1, more) =
      connect(ds)(AgentRepo.page(Some(runWire(RunState.Done.wire)), PageSize(2), Some(goalSlug))).toOption.get
    assertEquals((p1.map(_.id).toList, more), (done.take(2), true))
    val (p2, _) =
      connect(ds)(
        AgentRepo.page(
          Some(runWire(RunState.Done.wire)),
          PageSize(10),
          Some(goalSlug),
          before = runCursor(p1.last.id.value)
        )
      ).toOption.get
    assertEquals(p2.map(_.id).toList, done.drop(2))
    val (every, _) = connect(ds)(AgentRepo.page(None, PageSize(10), Some(goalSlug))).toOption.get
    assertEquals(every.size, 6)
    assertEquals(
      connect(ds)(AgentRepo.stateCounts(Some(goalSlug))).toMap.map((state, count) => (state.value: String) -> count),
      Map(RunState.Done.wire.value -> 5, RunState.Paused.wire.value -> 1)
    )
    // an OCR job is not a run, wherever it sits
    jobs(s"$tag-x", RunState.Done.wire.value, 1, "ingest", Some(goal))
    assertEquals(connect(ds)(AgentRepo.stateCounts(Some(goalSlug))).toMap.get(runWire(RunState.Done.wire)), Some(5))
    assert(connect(ds)(AgentRepo.page(None, PageSize(10), Some(goalSlug), upTo = runCursor(s"$tag-nope"))).isLeft)
    assertEquals(paused.size, 1)

  test("approvals: newest first by created_at then id, the cursor and upTo, a state filter, the counts"):
    val ids = transact(ds):
      (1 to 5).toList.map { i =>
        val at = f"1971-01-01T00:00:${i / 2}%02dZ"
        val state = if i == 2 then ApprovalState.Rejected.wire else ApprovalState.Open.wire
        sql"""INSERT INTO approvals (kind, citekey, status, proposal, state, created_at)
              VALUES ('status-change', ${s"$tag-a$i"}, 'contested', '{}'::jsonb, $state, $at::timestamptz)
              RETURNING id""".query[Long].run().head
      }
    val newest = ids.reverse // ids grow with i, and so does created_at
    // above the newest fixture lies everything other suites wrote; page from just above it
    val probe = transact(ds):
      sql"""INSERT INTO approvals (kind, citekey, proposal, state, created_at)
            VALUES ('status-change', ${s"$tag-top"}, '{}'::jsonb, 'zz', '1971-01-02T00:00:00Z'::timestamptz)
            RETURNING id""".query[Long].run().head
    val (all, more) =
      connect(ds)(AgentRepo.approvalPage(None, PageSize(3), before = runCursor(probe.toString))).toOption.get
    assertEquals(all.map(_.id), newest.take(3))
    assert(more)
    val (rest, none) =
      connect(ds)(AgentRepo.approvalPage(None, PageSize(3), before = runCursor(all.last.id.toString))).toOption.get
    assertEquals((rest.map(_.id), none), (newest.drop(3), false))
    val (open, _) =
      connect(ds)(
        AgentRepo.approvalPage(Some(ApprovalState.Open), PageSize(10), before = runCursor(probe.toString))
      ).toOption.get
    assertEquals(open.map(_.id), newest.filterNot(_ == ids(1)))
    val (down, older) =
      connect(ds)(
        AgentRepo.approvalPage(
          None,
          PageSize(10),
          before = runCursor(probe.toString),
          upTo = runCursor(newest(2).toString)
        )
      ).toOption.get
    assertEquals((down.map(_.id), older), (newest.take(3), true))
    assert(connect(ds)(AgentRepo.approvalCounts()).exists((s, n) => s.value == "zz" && n.value >= 1))
    assert(
      connect(ds)(AgentRepo.approvalPage(None, PageSize(3), before = Some(AgentRepo.PageCursor("not-a-number")))).isLeft
    )
    assert(connect(ds)(AgentRepo.approvalPage(None, PageSize(3), before = Some(AgentRepo.PageCursor("-1")))).isLeft)
