package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.UiNotices.Board

import com.augustnagro.magnum.*

/** The toolbar's notices against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise: an
  * approval's proposal and its decision both count as a change, a goal's edit does, the open ones are
  * counted, V083 left a baseline mark for every board, and a mark only moves forward. Assertions are
  * about this suite's own rows (other suites write approvals and goals at the same time). */
class UiRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb                = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val d = testPool(AppConfig.load().db.copy(url = testDb.get))
    Migrate.run(d)
    d

  private val ck   = "zz_ui_notice_src"
  private val goal = "zz-ui-notice-goal"

  private def tidy(): Unit =
    connect(ds):
      sql"DELETE FROM approvals WHERE citekey = $ck".update.run()
      sql"DELETE FROM goals WHERE slug = $goal".update.run()

  override def beforeEach(context: BeforeEach): Unit = if testDb.isDefined then tidy()
  override def afterEach(context: AfterEach): Unit   = if testDb.isDefined then tidy()

  private def board(b: Board) = UiRepo.counters(ds).boards.find(_.key == b.wire).getOrElse(fail(s"no ${b.wire}"))

  private def now(): String =
    connect(ds)(sql"""SELECT to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')""".query[String].run().head)

  test("every board is listed in order; the counted ones carry a count"):
    val cs = UiRepo.counters(ds).boards
    assertEquals(cs.map(_.key), Board.wires)
    assertEquals(cs.filter(_.count.isDefined).map(_.key), List("tasks", "approvals", "acquire"))
    assert(cs.forall(_.count.forall(_ >= 0)))

  test("the agents at a glance count the agent runs running and queued, and read the autopilot's switch"):
    final class Rollback extends RuntimeException
    val autopilot = connect(ds)(sql"SELECT enabled, drain_since IS NOT NULL FROM agent_autopilot WHERE id"
      .query[(Boolean, Boolean)].run().headOption.getOrElse((false, false)))
    // inside a transaction that is rolled back: no other suite's agent worker ever sees these runs
    try transact(ds) {
      sql"""INSERT INTO jobs (id, kind, status) VALUES ('zz-ui-glance-r', 'agent', 'running'),
              ('zz-ui-glance-q', 'agent', 'queued'), ('zz-ui-glance-i', 'ingest', 'running')""".update.run()
      val g = UiRepo.agentsGlance
      assert(g.running >= 1 && g.queued >= 1, g.toString)
      assertEquals((g.on, g.draining), autopilot)
      throw Rollback()
    } catch case _: Rollback => ()
    assertEquals(connect(ds)(sql"SELECT count(*) FROM jobs WHERE id LIKE 'zz-ui-glance-%'".query[Long].run().head), 0L)

  test("V083 left a mark for every board, so an upgraded corpus does not light up at once"):
    val marked = connect(ds)(sql"SELECT board FROM ui_seen".query[String].run()).toSet
    assert(Board.wires.forall(marked), marked.toString)

  test("an approval proposed is counted and new; read, it is not; its decision is news again"):
    val t0 = now()
    val _ = UiRepo.markSeen(ds, Board.Approvals, Some(t0))
    val before = board(Board.Approvals).count.get
    val id = connect(ds)(
      sql"""INSERT INTO approvals (kind, citekey, status, proposal) VALUES ('status-change', $ck, 'contested', '{}'::jsonb)
            RETURNING id""".query[Long].run().head)
    val proposed = board(Board.Approvals)
    assert(proposed.count.get >= before + 1, proposed.toString)
    assert(proposed.fresh, proposed.toString)
    assert(proposed.latest.exists(_ > t0), proposed.toString)
    val latest = proposed.latest.get
    assertEquals(UiRepo.markSeen(ds, Board.Approvals, Some(latest)), latest)
    val read = board(Board.Approvals)
    assertEquals(read.seen, Some(latest))
    // another suite may propose one at the same moment; ours is read either way
    assert(read.latest != Some(latest) || !read.fresh, read.toString)
    connect(ds)(sql"UPDATE approvals SET state = 'rejected', decided_at = now() WHERE id = $id".update.run())
    val decided = board(Board.Approvals)
    assert(decided.latest.exists(_ > latest), decided.toString)
    assert(decided.fresh, decided.toString)

  test("a goal changed after the mark is news"):
    val t0 = now()
    val _ = UiRepo.markSeen(ds, Board.Goals, Some(t0))
    connect(ds)(sql"INSERT INTO goals (slug, title) VALUES ($goal, 'zz notice')".update.run())
    val g = board(Board.Goals)
    assert(g.fresh && g.latest.exists(_ > t0), g.toString)
    assertEquals(g.count, None, "goals are not counted")

  test("a mark only moves forward, and no instant means now"):
    val t0 = now()
    val m1 = UiRepo.markSeen(ds, Board.Log, Some(t0))
    assert(m1 >= t0)
    assertEquals(UiRepo.markSeen(ds, Board.Log, Some("2000-01-01T00:00:00.000000Z")), m1)
    val m2 = UiRepo.markSeen(ds, Board.Log, None)
    assert(m2 >= m1, s"$m2 < $m1")
    assertEquals(board(Board.Log).seen, Some(m2))
