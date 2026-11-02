package com.histmersion.domain.activity

import com.histmersion.api.automation.Activity
import com.histmersion.api.automation.Activity.{Entry, Show}
import com.histmersion.config.AppConfig

import com.augustnagro.magnum.*

import java.time.Instant
import javax.sql.DataSource

/** The activity list merges runs, jobs, worklog lines and a person's decisions into one time-ordered list: the same
  * answers from the in-memory service and from the database's (on a scratch database of its own, gated on
  * HISTMERSION_TEST_DB_URL).
  */
class ActivityServiceSuite extends com.histmersion.db.DbSuite:

  private val now = Instant.parse("2026-10-30T12:00:00Z")
  private def ago(min: Int): Instant = now.minusSeconds(min * 60L)

  /** What both services hold, by name: the rows of the store, and the keys the list gives them. */
  final private case class World(service: ActivityService, key: Map[String, String])

  private def inMemory: World =
    val m = ActivityService.InMemory()
    val names = Map(
      "r1" -> "run:r1",
      "j1" -> "job:j1",
      "j0" -> "job:j0",
      "l1" -> "log:1",
      "l2" -> "log:2",
      "l3" -> "log:3",
      "a5" -> "decision:1"
    )
    List(
      Entry(
        "run:r1",
        "run",
        ago(1).toString,
        state = "running",
        what = "review-article",
        goal = Some("g1"),
        job = Some("r1"),
        article = Some("sea-peoples"),
        target = Some("sea-peoples"),
        targetKind = Some("article")
      ),
      Entry(
        "job:j1",
        "job",
        ago(2).toString,
        state = "failed",
        what = "media-import",
        error = Some("HTTP 500"),
        job = Some("j1")
      ),
      Entry("job:j0", "job", ago(60 * 24 * 9).toString, state = "failed", what = "basemap-fetch", job = Some("j0")),
      Entry(
        "log:1",
        "log",
        ago(3).toString,
        text = "reviewed",
        goal = Some("g1"),
        job = Some("r1"),
        by = Some("agent")
      ),
      Entry("log:2", "log", ago(3).toString, text = "fetched", job = Some("j1"), by = Some("job")),
      Entry("log:3", "log", ago(3).toString, text = "a person's note"),
      Entry(
        "decision:1",
        "decision",
        ago(4).toString,
        state = "approved",
        what = "edge",
        goal = Some("g1"),
        job = Some("r1"),
        approval = Some(1L)
      )
    ).foreach(m.add)
    World(m, names)

  private val testDb = com.histmersion.db.TestDb.url
  private lazy val scratch = ScratchDatabases("activity", AppConfig.load().db.copy(url = testDb.get))
  override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()

  private def onDatabase: World =
    val ds = scratch("list")
    seed(ds)
    World(
      ActivityService(ds),
      Map(
        "r1" -> "run:r1",
        "j1" -> "job:j1",
        "j0" -> "job:j0",
        "l1" -> "log:1",
        "l2" -> "log:2",
        "l3" -> "log:3",
        "a5" -> "decision:1"
      )
    )

  private def seed(ds: DataSource): Unit =
    def t(i: Instant) = i.toString
    transact(ds):
      sql"""INSERT INTO jobs (id, kind, status, pipeline, target_kind, target_id, goal_slug, created_at, updated_at)
            VALUES ('r1', 'agent', 'running', 'review-article', 'article', 'sea-peoples', 'g1', ${t(
          ago(1)
        )}::timestamptz, ${t(ago(1))}::timestamptz)""".update.run()
      sql"""INSERT INTO jobs (id, kind, status, error, created_at, updated_at)
            VALUES ('j1', 'media-import', 'failed', 'HTTP 500', ${t(ago(2))}::timestamptz, ${t(ago(1))}::timestamptz),
                   ('j0', 'basemap-fetch', 'failed', NULL, ${t(ago(60 * 24 * 9))}::timestamptz, ${t(
          ago(60 * 24 * 9)
        )}::timestamptz)""".update.run()
      // three lines of one instant (by a run, by a job, by a person), a note block and a backlog item (neither listed)
      sql"""INSERT INTO queue_items (list, text, status, created_at, goal_slug, created_job) VALUES
            ('log', 'reviewed', 'open', ${t(ago(3))}::timestamptz, 'g1', 'r1'),
            ('log', 'fetched', 'open', ${t(ago(3))}::timestamptz, NULL, 'j1'),
            ('log', 'a person''s note', 'open', ${t(ago(3))}::timestamptz, NULL, NULL),
            ('log', '## preamble', 'note', ${t(ago(3))}::timestamptz, NULL, NULL),
            ('tasks', 'a backlog item', 'open', ${t(ago(3))}::timestamptz, 'g1', 'r1')""".update.run()
      // a person's decision, one the autopilot made, one still open
      sql"""INSERT INTO approvals (job_id, kind, citekey, proposal, state, note, result, created_at, decided_at) VALUES
            ('r1', 'edge', 'drake2012', '{}'::jsonb, 'approved', 'looks right', 'applied', ${t(
          ago(30)
        )}::timestamptz, ${t(ago(4))}::timestamptz),
            ('r1', 'edge', 'drake2013', '{}'::jsonb, 'approved', 'autopilot: checked', 'applied', ${t(
          ago(30)
        )}::timestamptz, ${t(ago(5))}::timestamptz),
            ('r1', 'edge', 'drake2014', '{}'::jsonb, 'open', NULL, NULL, ${t(ago(30))}::timestamptz, NULL)""".update
        .run()
    ()

  private def keysOf(w: World, q: ActivityQuery): List[String] = w.service.page(q, now).entries.map(_.key)
  private def named(w: World, names: String*): List[String] = names.toList.map(w.key)

  private def spec(label: String, world: => World): Unit =
    lazy val w = world

    test(s"$label: one list, newest first; one instant's entries by key"):
      assertEquals(keysOf(w, ActivityQuery()), named(w, "r1", "j1", "l3", "l2", "l1", "a5", "j0"))

    test(s"$label: pages of any size walk the whole list once"):
      for size <- 1 to 4 do
        def walk(q: ActivityQuery, acc: List[String]): List[String] =
          val p = w.service.page(q, now)
          val got = acc ++ p.entries.map(_.key)
          p.next.fold(got)(c => walk(q.copy(before = ActivityCursor.parse(c).toOption), got))
        assertEquals(
          walk(ActivityQuery(limit = size), Nil),
          named(w, "r1", "j1", "l3", "l2", "l1", "a5", "j0"),
          s"size $size"
        )

    test(s"$label: the chips"):
      assertEquals(keysOf(w, ActivityQuery(show = Show.Running)), named(w, "r1"))
      assertEquals(keysOf(w, ActivityQuery(show = Show.Agents)), named(w, "r1", "l1"))
      assertEquals(keysOf(w, ActivityQuery(show = Show.Jobs)), named(w, "j1", "l2", "j0"))
      assertEquals(keysOf(w, ActivityQuery(show = Show.Yours)), named(w, "a5"))
      assertEquals(keysOf(w, ActivityQuery(show = Show.Problems)), named(w, "j1", "j0"))

    test(s"$label: a goal's history and a time bound"):
      assertEquals(keysOf(w, ActivityQuery(goal = Some("g1"))), named(w, "r1", "l1", "a5"))
      assertEquals(keysOf(w, ActivityQuery(since = Some(ago(2)))), named(w, "r1", "j1"))

    test(s"$label: each entry names what it touched"):
      val es = w.service.page(ActivityQuery(), now).entries.map(e => e.key -> e).toMap
      val run = es(w.key("r1"))
      assertEquals(
        (run.state, run.what, run.goal, run.article, run.job, run.endedAt),
        ("running", "review-article", Some("g1"), Some("sea-peoples"), Some("r1"), None)
      )
      assertEquals(
        (es(w.key("l1")).by, es(w.key("l1")).job, es(w.key("l1")).goal),
        (Some("agent"), Some("r1"), Some("g1"))
      )
      assertEquals((es(w.key("l2")).by, es(w.key("l3")).by), (Some("job"), None))
      val decision = es(w.key("a5"))
      assertEquals(
        (decision.state, decision.what, decision.goal, decision.job, decision.approval),
        ("approved", "edge", Some("g1"), Some("r1"), Some(1L))
      )
      assertEquals(es(w.key("j1")).error, Some("HTTP 500"))

    test(s"$label: the counts: one running, one problem this week"):
      assertEquals(w.service.page(ActivityQuery(), now).counts, Activity.Counts(running = 1, problemsWeek = 1))

  spec("in memory", inMemory)

  private def dbSpec(label: String): Unit =
    if testDb.isEmpty then test(s"$label (on a database)")(assume(false, "HISTMERSION_TEST_DB_URL is unset"))
    else spec(label, onDatabase)
  dbSpec("on the database")
