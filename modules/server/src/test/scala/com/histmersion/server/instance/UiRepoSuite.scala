package com.histmersion
package server.instance

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.util.database.Transactor

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.instance.UiNotices
import com.histmersion.api.instance.UiNotices.Board
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.library.Citekey
import com.histmersion.util.database.Migrate

import com.augustnagro.magnum.*
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.lint.{LintRule, tolerated}

/** The toolbar's notices against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise: an approval's proposal
  * and its decision both count as a change, a goal's edit does, the open ones are counted, V083 left a baseline mark
  * for every board, and each counter carries the seen mark it is given. Assertions are about this suite's own rows
  * (other suites write approvals and goals at the same time).
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class UiRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d

  private val ck = Citekey("zz_ui_notice_src")
  private val goal = Goal.Slug("zz-ui-notice-goal")

  private def tidy(): Unit =
    connect(ds):
      sql"DELETE FROM approvals WHERE citekey = $ck".update.run()
      sql"DELETE FROM goals WHERE slug = $goal".update.run()

  override def beforeEach(context: BeforeEach): Unit = if testDb.isDefined then tidy()
  override def afterEach(context: AfterEach): Unit = if testDb.isDefined then tidy()

  /** The seen marks as the person's service keeps them (`ui_seen`, written here directly: that service is the
    * server's).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def seen(): Map[Board.Wire, UiNotices.Counter.Seen] =
    connect(ds)(
      sql"""SELECT board, to_char(seen_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"') FROM ui_seen"""
        .query[(String, String)]
        .run()
        .map((b, at) => Board.Wire(b) -> UiNotices.Counter.Seen(at))
        .toMap
    )

  private def markSeen(b: Board, at: UiNotices.Counter.Seen): Unit =
    connect(ds)(
      sql"""INSERT INTO ui_seen (board, seen_at) VALUES (${b.wire}, $at::timestamptz)
            ON CONFLICT (board) DO UPDATE SET seen_at = greatest(ui_seen.seen_at, EXCLUDED.seen_at)""".update
        .run(): Unit
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def board(b: Board) =
    Transactor(ds).read(UiRepo.counters(seen())).boards.find(_.key == b.wire).getOrElse(fail(s"no ${b.wire}"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def now(): UiNotices.Counter.Seen =
    UiNotices.Counter.Seen(
      connect(ds)(
        sql"""SELECT to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')""".query[String].run().head
      )
    )

  test("every board is listed in order; the counted ones carry a count"):
    val cs = Transactor(ds).read(UiRepo.counters(seen())).boards
    assertEquals(cs.map(_.key), Board.wires)
    assertEquals(cs.filter(_.count.isDefined).map(_.key), List("tasks", "approvals", "acquire"))
    assert(cs.forall(_.count.forall(_.value >= 0)))

  test("the agents at a glance count the agent runs running and queued, and read the autopilot's switch"):
    final class Rollback extends RuntimeException
    val autopilot = connect(ds)(
      sql"SELECT enabled, drain_since IS NOT NULL FROM agent_autopilot WHERE id"
        .query[(Boolean, Boolean)]
        .run()
        .headOption
        .getOrElse((false, false))
    )
    // inside a transaction that is rolled back: no other suite's agent worker ever sees these runs
    try
      transact(ds) {
        sql"""INSERT INTO jobs (id, kind, status, creator) VALUES ('zz-ui-glance-r', 'agent', 'running', jsonb_build_object('kind', 'unknown')),
              ('zz-ui-glance-q', 'agent', 'queued', jsonb_build_object('kind', 'unknown')), ('zz-ui-glance-i', 'ingest', 'running', jsonb_build_object('kind', 'unknown'))""".update
          .run()
        val g = UiRepo.agentsGlance
        assert(g.running.value >= 1 && g.queued.value >= 1, g.toString)
        assertEquals((g.on, g.draining), autopilot)
        throw Rollback()
      }
    catch case _: Rollback => ()
    assertEquals(connect(ds)(sql"SELECT count(*) FROM jobs WHERE id LIKE 'zz-ui-glance-%'".query[Long].run().head), 0L)

  test("V083 left a mark for every board, so an upgraded corpus does not light up at once"):
    val marked = connect(ds)(sql"SELECT board FROM ui_seen".query[String].run()).map(Board.Wire(_)).toSet
    assert(Board.wires.forall(marked), marked.toString)

  test("an approval proposed is counted and new; read, it is not; its decision is news again"):
    val t0 = now()
    markSeen(Board.Approvals, t0)
    val before = board(Board.Approvals).count.get.value
    val id = connect(ds)(
      sql"""INSERT INTO approvals (kind, citekey, status, proposal, creator) VALUES ('status-change', $ck, 'contested', '{}'::jsonb, jsonb_build_object('kind', 'unknown'))
            RETURNING id""".query[Long].run().head
    )
    val proposed = board(Board.Approvals)
    assert(proposed.count.get.value >= before + 1, proposed.toString)
    assert(proposed.fresh.value, proposed.toString)
    assert(proposed.latest.exists(_.value > t0.value), proposed.toString)
    val latest = proposed.latest.get
    markSeen(Board.Approvals, UiNotices.Counter.Seen(latest.value))
    val read = board(Board.Approvals)
    assertEquals(read.seen.map(_.value), Some(latest.value))
    // another suite may propose one at the same moment; ours is read either way
    assert(read.latest != Some(latest) || !read.fresh.value, read.toString)
    connect(ds)(sql"UPDATE approvals SET state = 'rejected', decided_at = now() WHERE id = $id".update.run())
    val decided = board(Board.Approvals)
    assert(decided.latest.exists(_.value > latest.value), decided.toString)
    assert(decided.fresh.value, decided.toString)

  test("a goal changed after the mark is news"):
    val t0 = now()
    markSeen(Board.Goals, t0)
    connect(ds)(
      sql"INSERT INTO goals (slug, title, creator) VALUES ($goal, 'zz notice', jsonb_build_object('kind', 'unknown'))".update
        .run()
    )
    val g = board(Board.Goals)
    assert(g.fresh.value && g.latest.exists(_.value > t0.value), g.toString)
    assertEquals(g.count, None, "goals are not counted")

  test("a board's counter carries the seen mark it is given, and none for a board never opened"):
    val cs = Transactor(ds)
      .read(UiRepo.counters(Map(Board.Log.wire -> UiNotices.Counter.Seen("2026-10-31T12:00:00.000000Z"))))
      .boards
    assertEquals(cs.find(_.key == Board.Log.wire).flatMap(_.seen), Some("2026-10-31T12:00:00.000000Z"))
    assertEquals(cs.find(_.key == Board.Tasks.wire).flatMap(_.seen), None)
