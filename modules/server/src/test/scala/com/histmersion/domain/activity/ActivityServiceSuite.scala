package com.histmersion
package domain.activity
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.database.NewtypeColumns.given

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Activity
import com.histmersion.api.automation.Activity.{Entry, Show}
import com.histmersion.config.AppConfig

import com.augustnagro.magnum.*

import java.time.Instant
import javax.sql.DataSource
import com.histmersion.db.DbSuite
import com.histmersion.api.automation.Goal
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig

/** The activity list merges runs, jobs, worklog lines and a person's decisions into one time-ordered list: the same
  * answers from the in-memory service and from the database's (on a scratch database of its own, gated on
  * HISTMERSION_TEST_DB_URL).
  */
class ActivityServiceSuite extends DbSuite:

  private val now = Instant.parse("2026-10-30T12:00:00Z")
  private def ago(min: Int): Instant = now.minusSeconds(min * 60L)

  /** What both services hold, by name: the rows of the store, and the keys the list gives them. */
  final private case class World(service: ActivityService, key: Map[String, Entry.Key])

  private def at(i: Instant): Entry.At = Entry.At(i.toString)

  private def inMemory: World =
    val m = ActivityService.InMemory()
    val names = Map(
      "r1" -> Entry.Key("run:r1"),
      "j1" -> Entry.Key("job:j1"),
      "j0" -> Entry.Key("job:j0"),
      "l1" -> Entry.Key("log:1"),
      "l2" -> Entry.Key("log:2"),
      "l3" -> Entry.Key("log:3"),
      "a5" -> Entry.Key("decision:1")
    )
    List(
      Entry(
        Entry.Key("run:r1"),
        Entry.Kind("run"),
        at(ago(1)),
        state = Entry.State("running"),
        what = Entry.What("review-article"),
        goal = Some(Goal.Slug("g1")),
        job = Some(Entry.Job("r1")),
        article = Some(Entry.Article("sea-peoples")),
        target = Some(Entry.Target("sea-peoples")),
        targetKind = Some(Entry.TargetKind("article"))
      ),
      Entry(
        Entry.Key("job:j1"),
        Entry.Kind("job"),
        at(ago(2)),
        state = Entry.State("failed"),
        what = Entry.What("media-import"),
        error = Some(Problem.Unclassified(Legacy.Text("HTTP 500"))),
        job = Some(Entry.Job("j1"))
      ),
      Entry(
        Entry.Key("job:j0"),
        Entry.Kind("job"),
        at(ago(60 * 24 * 9)),
        state = Entry.State("failed"),
        what = Entry.What("basemap-fetch"),
        job = Some(Entry.Job("j0"))
      ),
      Entry(
        Entry.Key("log:1"),
        Entry.Kind("log"),
        at(ago(3)),
        text = Entry.Text("reviewed"),
        goal = Some(Goal.Slug("g1")),
        job = Some(Entry.Job("r1")),
        by = Some(Entry.By("agent"))
      ),
      Entry(
        Entry.Key("log:2"),
        Entry.Kind("log"),
        at(ago(3)),
        text = Entry.Text("fetched"),
        job = Some(Entry.Job("j1")),
        by = Some(Entry.By("job"))
      ),
      Entry(Entry.Key("log:3"), Entry.Kind("log"), at(ago(3)), text = Entry.Text("a person's note")),
      Entry(
        Entry.Key("decision:1"),
        Entry.Kind("decision"),
        at(ago(4)),
        state = Entry.State("approved"),
        what = Entry.What("edge"),
        goal = Some(Goal.Slug("g1")),
        job = Some(Entry.Job("r1")),
        approval = Some(Entry.Approval(1L))
      )
    ).foreach(m.add)
    World(m, names)

  private val testDb = TestDb.url
  private lazy val scratch = ScratchDatabases("activity", AppConfig.load().db.copy(url = DbConfig.Url(testDb.get)))
  override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()

  private def onDatabase: World =
    val ds = scratch("list")
    seed(ds)
    World(
      ActivityService(ds),
      Map(
        "r1" -> Entry.Key("run:r1"),
        "j1" -> Entry.Key("job:j1"),
        "j0" -> Entry.Key("job:j0"),
        "l1" -> Entry.Key("log:1"),
        "l2" -> Entry.Key("log:2"),
        "l3" -> Entry.Key("log:3"),
        "a5" -> Entry.Key("decision:1")
      )
    )

  private def seed(ds: DataSource): Unit =
    def t(i: Instant) = i.toString
    // the job's problems as the server stores them: a list of entries
    val failed = NonEmptyVector.of[Problem](Problem.Unclassified(Legacy.Text("HTTP 500")))
    transact(ds):
      sql"""INSERT INTO jobs (id, kind, status, pipeline, target_kind, target_id, goal_slug, created_at, updated_at)
            VALUES ('r1', 'agent', 'running', 'review-article', 'article', 'sea-peoples', 'g1', ${t(
          ago(1)
        )}::timestamptz, ${t(ago(1))}::timestamptz)""".update.run()
      sql"""INSERT INTO jobs (id, kind, status, error, created_at, updated_at)
            VALUES ('j1', 'media-import', 'failed', $failed, ${t(ago(2))}::timestamptz, ${t(ago(1))}::timestamptz),
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

  private def keysOf(w: World, q: ActivityQuery): List[Entry.Key] = w.service.page(q, now).entries.map(_.key)
  private def named(w: World, names: String*): List[Entry.Key] = names.toList.map(w.key)

  private def spec(label: String, world: => World): Unit =
    lazy val w = world

    test(s"$label: one list, newest first; one instant's entries by key"):
      assertEquals(keysOf(w, ActivityQuery()), named(w, "r1", "j1", "l3", "l2", "l1", "a5", "j0"))

    test(s"$label: pages of any size walk the whole list once"):
      for size <- 1 to 4 do
        def walk(q: ActivityQuery, acc: List[Entry.Key]): List[Entry.Key] =
          val p = w.service.page(q, now)
          val got = acc ++ p.entries.map(_.key)
          p.next.fold(got)(c => walk(q.copy(before = ActivityCursor.parse(ActivityCursor.Wire(c.value)).toOption), got))
        assertEquals(
          walk(ActivityQuery(limit = ActivityQuery.Limit(size)), Nil),
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
      assertEquals(keysOf(w, ActivityQuery(goal = Some(ActivityQuery.Goal("g1")))), named(w, "r1", "l1", "a5"))
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
      assertEquals(es(w.key("j1")).error.map(_.sentence), Some("HTTP 500"))

    test(s"$label: the counts: one running, one problem this week"):
      assertEquals(
        w.service.page(ActivityQuery(), now).counts,
        Activity.Counts(running = Activity.Counts.Running(1), problemsWeek = Activity.Counts.ProblemsWeek(1))
      )

  spec("in memory", inMemory)

  private def dbSpec(label: String): Unit =
    if testDb.isEmpty then test(s"$label (on a database)")(assume(false, "HISTMERSION_TEST_DB_URL is unset"))
    else spec(label, onDatabase)
  dbSpec("on the database")
