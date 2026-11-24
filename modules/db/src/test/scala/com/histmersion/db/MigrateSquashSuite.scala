package com.histmersion
package db

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence
import hearth.kindlings.optics.syntax.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.config.AppConfig

import javax.sql.DataSource
import org.flywaydb.core.Flyway
import com.augustnagro.magnum.*
import com.histmersion.util.database.{Migrate, Transactor}
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.api.problem.StoredShapes
import com.histmersion.api.problem.Answer.entry
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Managers.ManagerInfo
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.config.DbConfig
import com.histmersion.util.database.TestPermits.given
import com.histmersion.lint.{LintRule, tolerated}

/** The migration chain since its squash ([[Migrate.squashHistory]]): a fresh database gets the squash, then each later
  * migration; a database the chain before it built keeps every row and has its history replaced by a baseline at 109,
  * then gets the later migrations; one that chain had not finished is refused, untouched.
  *
  * And `V110__problems_as_entries.sql` on a database at 109, as a master version left it: the four columns that kept a
  * problem's text (`jobs.error`, `agent_steps.error`, `candidate_watches.last_error`, `plan_pools.refusal`) are seeded
  * with the texts the master writers wrote ([[Master]]: their formats, copied from master's code), migrated, and read
  * back. Each text becomes a one-element array of the entry the code writes for the problem [[StoredShapes]] reads it
  * as; any other text keeps its words whole.
  *
  * In databases of their own beside HISTMERSION_TEST_DB_URL's, dropped afterwards; skipped without it.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class MigrateSquashSuite extends DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg = AppConfig.load()
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val admin = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val dbs = ScratchDatabases("histmersion_squash", cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
  private val created = scala.collection.mutable.ListBuffer.empty[String]

  /** The migrations since the squash, by version, in order: each new migration adds itself here. */
  private val Chain = List("109", "110", "111", "112", "113", "115", "116")
  private val Latest = Migrate.Version("116")

  private val UpdatedAt =
    """SELECT t || ':' || string_agg(u::text, ',' ORDER BY k) FROM (
      |  SELECT 'jobs' t, id k, updated_at u FROM jobs UNION ALL
      |  SELECT 'events', id, updated_at FROM events UNION ALL
      |  SELECT 'places', id, updated_at FROM places WHERE id LIKE 'zz-%' UNION ALL
      |  SELECT 'sources', citekey, updated_at FROM sources UNION ALL
      |  SELECT 'assets', name, updated_at FROM assets) x GROUP BY t ORDER BY t""".stripMargin

  private val QueueRowsAtSquash =
    "SELECT md5(string_agg((t.id, t.list, t.text, t.topic, t.status, t.created_at, t.done_at, t.reason, t.uid, " +
      "t.origin, t.updated_at, t.goal_slug, t.created_job, t.closed_job)::text, ',' ORDER BY t.id)) FROM queue_items t"

  private def exec(ds: DataSource, stmt: String): Unit =
    val c = ds.getConnection
    try
      val st = c.createStatement()
      try { val _ = st.execute(stmt) }
      finally st.close()
    finally c.close()

  private def strings(ds: DataSource, query: String): List[String] =
    val c = ds.getConnection
    try
      val st = c.createStatement()
      try
        val rs = st.executeQuery(query)
        Iterator.continually(rs).takeWhile(_.next()).map(_.getString(1)).toList
      finally st.close()
    finally c.close()

  private def emptyDatabase(name: String): DataSource =
    exec(admin, s"DROP DATABASE IF EXISTS $name WITH (FORCE)")
    exec(admin, s"CREATE DATABASE $name")
    created += name
    testPool(cfg.db.modify(_.url).setTo(dbs.urlOf(name)))

  /** The database at the squash, 109, as a version before V110 left it: the migrations up to it, and no further. */
  private def atSquash(ds: DataSource): Unit =
    Migrate.ensureRole(ds, Migrate.QueryRole)
    val _ = Flyway.configure().configuration(Migrate.flyway(ds).getConfiguration).target("109").load().migrate()

  /** The history the chain before the squash left: `001` to `top`, each a successful SQL migration. */
  private def oldChain(ds: DataSource, top: Int): Unit =
    exec(ds, "DELETE FROM flyway_schema_history")
    exec(
      ds,
      s"""INSERT INTO flyway_schema_history
         |  (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
         |SELECT v, lpad(v::text, 3, '0'), 'm' || v, 'SQL', 'V' || lpad(v::text, 3, '0') || '__m.sql', v, 'x', 1, true
         |FROM generate_series(1, $top) v""".stripMargin
    )

  private val history = "SELECT version || ' ' || type FROM flyway_schema_history ORDER BY installed_rank"

  override def afterSuite(): Unit =
    if testDb.isDefined then
      created.foreach(n => exec(admin, s"DROP DATABASE IF EXISTS $n WITH (FORCE)"))
      dbs.dropAll()

  test("a fresh database gets the squash, then each migration after it"):
    val ds = emptyDatabase("histmersion_squash_fresh_test")
    assertEquals(Migrate.run(ds), Latest)
    assertEquals(strings(ds, history), Chain.map(v => s"$v SQL"))
    assertEquals(strings(ds, "SELECT count(*)::text FROM instance"), List("1"))
    assert(Migrate.validate(ds).isRight, Migrate.validate(ds).toString)

  test("a database the old chain built keeps its rows; its history becomes one baseline, then the later migrations"):
    val ds = emptyDatabase("histmersion_squash_old_test")
    atSquash(ds)
    exec(ds, "INSERT INTO queue_items (list, text) VALUES ('tasks', 'kept')")
    val before = strings(ds, QueueRowsAtSquash)
    oldChain(ds, 109)
    assertEquals(Migrate.run(ds), Latest)
    val baseline = s"${Migrate.SquashedAt} BASELINE" :: Chain.drop(1).map(v => s"$v SQL")
    assertEquals(strings(ds, history), baseline)
    assertEquals(strings(ds, QueueRowsAtSquash), before)
    assert(Migrate.validate(ds).isRight, Migrate.validate(ds).toString)
    assertEquals(Migrate.run(ds), Latest, "idempotent")
    assertEquals(strings(ds, history), baseline)

  test("a database the old chain had not finished is refused, its history untouched"):
    val ds = emptyDatabase("histmersion_squash_behind_test")
    val _ = Migrate.run(ds)
    oldChain(ds, 108)
    val e = intercept[IllegalStateException](Migrate.run(ds))
    assert(e.getMessage.contains("schema 108"), e.getMessage)
    assertEquals(strings(ds, "SELECT count(*)::text FROM flyway_schema_history"), List("108"))
    assert(Migrate.validate(ds).left.exists(_.sentence.contains("schema 108")), Migrate.validate(ds).toString)

  // ── V110: problems as their entries ──────────────────────────────────────────────────────────────

  /** What the master writers stored (master `api/automation/Stopped.scala` `of`, lines 77–82; `JobState` line 16; the
    * literals each writer passed, cited at [[Texts]]).
    */
  private object Master:
    val Interrupted = "interrupted" // JobState.Interrupted
    val DrainingText = "stopped while the autopilot drains: it resumes once the drain is lifted" // Stopped.scala:72
    def paused(reasonWire: String, detail: String): String = s"$reasonWire: $detail" // Stopped.of, Cause.Paused
    def exhausted(reasonWire: String, lead: String, role: String, rest: String): String =
      s"$reasonWire: ${lead}all models for role $role are $rest" // Stopped.of, Cause.Exhausted
    def notAsked(why: String): String = s"not asked this sweep — $why" // CandidateSearch.scala:125

  /** Each master text, with the writer (on `master`) that wrote it. */
  private val Texts: Vector[String] = Vector(
    Master.Interrupted, // JobRepo.scala:238, Engine.scala:232
    Master.DrainingText, // AcquireRecheckService.scala:413
    Master.paused("interrupted", "interrupted"), // Engine.scala:431, :452
    Master.paused("requested", "paused on request"), // Engine.scala:514, :452
    Master.paused("requested", "paused before it started"), // Engine.scala:591
    Master.paused("interrupted", "the upload is no longer held by this process"), // IngestQueue.scala:167
    Master.paused("budget", "enrich: the run's budget is exhausted"), // RunCtx.scala:518
    Master.paused("provider", "anthropic answered HTTP 529 — overloaded"), // RunCtx.scala:560
    Master.paused("stalled", "no sign of life for 31 min at step 4 (stall 1 of 3)"), // Engine.scala:180
    Master.exhausted("plan-quota", "enrich: ", "enricher", "below their plan floor or allowance; at 10:00 (x)"),
    Master.exhausted("allowance", "", "writer", "below their allowance; never (y)"), // ModelPicker.scala:353
    Master.notAsked("CORE asked to wait until 2025-11-10T00:00:00Z"), // CandidateSearch.scala:125
    "cancelled", // Engine.scala, a cancel
    "the ChatGPT plan does not allow ordinary usage now (ordinaryUsageAllowed: false)", // PlanPools.scala:787
    "IllegalStateException: pg_dump vanished\n\tat x.Y(Y.scala:1)\n" + ("x" * 1500), // Engine.scala, a crash
    "a merge apply is 'still' running — \"quoted\" \\ back\\slash", // characters JSON escapes
    ""
  )

  private lazy val problems: DataSource = emptyDatabase("histmersion_squash_problems_test")

  /** The entry the code writes for a master text, compared as JSON whatever the key order. */
  private def written(text: String): Json = StoredShapes.read(text).entry.asJson.canonical

  test("V110: each master text becomes the entry of the problem it reads as, its words kept; the code reads it back"):
    val ds = problems
    atSquash(ds)
    Transactor(ds).write {
      sql"INSERT INTO goals (slug, title) VALUES ('g', 'G')".update.run()
      Texts.zipWithIndex.foreach { (text, i) =>
        val id = s"job-$i"
        sql"INSERT INTO jobs (id, kind, status, error) VALUES ($id, 'agent', 'failed', $text)".update.run()
        sql"""INSERT INTO agent_steps (job_id, seq, attempt, name, kind, status, error)
              VALUES ($id, 1, 1, 'enrich', 'llm', 'failed', $text)""".update.run()
        val subject = s"s$i"
        sql"""INSERT INTO candidate_watches (goal, kind, subject, index_name, last_error)
              VALUES ('g', 'query', $subject, 'core', $text)""".update.run()
        val pool = s"pool-$i"
        sql"INSERT INTO plan_pools (pool, refusal) VALUES ($pool, $text)".update.run()
      }
      sql"INSERT INTO jobs (id, kind, status) VALUES ('job-none', 'ingest', 'done')".update.run()
      // a manager's runs, paused before the upgrade: one waits for an allowance, one for a person
      sql"INSERT INTO agent_managers (id, kind) VALUES ('m1', 'review')".update.run()
      val allowance = Master.exhausted("allowance", "", "writer", "below their allowance; never (y)")
      val requested = Master.paused("requested", "paused on request")
      sql"""INSERT INTO jobs (id, kind, status, error, manager_id)
            VALUES ('run-a', 'agent', 'paused', $allowance, 'm1'), ('run-r', 'agent', 'paused', $requested, 'm1')""".update
        .run()
    }
    assertEquals(Migrate.run(ds), Latest)

    Transactor(ds).read {
      Texts.zipWithIndex.foreach { (text, i) =>
        val id = s"job-$i"
        val read = StoredShapes.read(text)
        val clue = s"'$text'"
        // each column: a one-element array of the entry, read back by the code, readable in SQL
        List(
          sql"SELECT error::text FROM jobs WHERE id = $id",
          sql"SELECT error::text FROM agent_steps WHERE job_id = $id",
          sql"SELECT last_error::text FROM candidate_watches WHERE subject = ${s"s$i"}",
          sql"SELECT refusal::text FROM plan_pools WHERE pool = ${s"pool-$i"}"
        ).foreach { q =>
          assertEquals(RawJson.parse(q.query[String].run().head).map(_.canonical), Right(Json.arr(written(text))), clue)
        }
        List(
          sql"SELECT error FROM jobs WHERE id = $id",
          sql"SELECT error FROM agent_steps WHERE job_id = $id",
          sql"SELECT last_error FROM candidate_watches WHERE subject = ${s"s$i"}",
          sql"SELECT refusal FROM plan_pools WHERE pool = ${s"pool-$i"}"
        ).foreach { q =>
          assertEquals(q.query[Option[NonEmptyVector[Problem]]].run().head, Some(NonEmptyVector.of(read)), clue)
        }
        assertEquals(
          sql"SELECT error -> 0 ->> 'text' FROM jobs WHERE id = $id".query[String].run().head,
          read.sentence,
          s"readable in SQL: $clue"
        )
      }
      assertEquals(sql"SELECT error FROM jobs WHERE id = 'job-none'".query[Option[String]].run().head, None)
    }

  test("V110: an unrecognised text keeps every character, in the entry's data and its text"):
    Transactor(problems).read {
      Texts.zipWithIndex.filter((t, _) => StoredShapes.read(t).isInstanceOf[Problem.Stored.Unrecognised]).foreach {
        (text, i) =>
          val id = s"job-$i"
          assertEquals(
            sql"SELECT error -> 0 -> 'data' ->> 'text', error -> 0 ->> 'text' FROM jobs WHERE id = $id"
              .query[(String, String)]
              .run()
              .head,
            (text, text)
          )
      }
    }

  test("V110: a pause migrated from its words occupies its manager's place by its case, as one written now does"):
    // after the first V110 test: run-a (allowance) and run-r (requested) were migrated
    assertEquals(Transactor(problems).read(ManagerRepo.running(ManagerInfo.Id("m1"))), RowCount(1), "the allowance")
    Transactor(problems).write {
      val planQuota = NonEmptyVector.of[Problem](
        Problem.Run
          .Halted(Problem.Run.PauseCause.PlanQuota, Problem.Run.Skipped(Problem.Run.Skip.Cooling(Problem.EpochMs(0L))))
      )
      sql"""INSERT INTO jobs (id, kind, status, error, manager_id, creator)
            VALUES ('run-q', 'agent', 'paused', $planQuota, 'm1', '{"kind": "unknown"}')""".update.run()
    }
    assertEquals(Transactor(problems).read(ManagerRepo.running(ManagerInfo.Id("m1"))), RowCount(2), "and a plan pool's")

  test("V112: each acquisition check becomes one legacy attempt, its reason and count kept"):
    val ds = emptyDatabase("histmersion_squash_attempts_test")
    Migrate.ensureRole(ds, Migrate.QueryRole)
    val _ = Flyway.configure().configuration(Migrate.flyway(ds).getConfiguration).target("111").load().migrate()
    exec(ds, "INSERT INTO queue_items (id, list, text) VALUES (41, 'acquire', 'Get https://doi.org/10.5555/zq.a')")
    exec(
      ds,
      """INSERT INTO acquire_checks (item_id, checked_at, generation, outcome, reason, checks) VALUES
        |  (41, '2026-10-27T10:00:00Z', 'g1', 'no-copy', 'checked 2026-10-27: no legal copy (unpaywall: no copy — none)', 3)""".stripMargin
    )
    assertEquals(Migrate.run(ds), Latest)
    assertEquals(
      strings(
        ds,
        """SELECT item_id || '|' || to_char(at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI') || '|' || trigger || '|' ||
          |       generation || '|' || outcome || '|' || legacy_reason || '|' || legacy_checks || '|' || answers::text
          |  FROM acquire_attempts""".stripMargin
      ),
      List(
        "41|2026-10-27T10:00|legacy|legacy|legacy|checked 2026-10-27: no legal copy (unpaywall: no copy — none)|3|[]"
      )
    )
    assertEquals(strings(ds, "SELECT to_regclass('public.acquire_checks')::text"), List(null))

  test("V113: each item's creator from stored facts only, its actor from its topic; nothing else of the row moves"):
    val ds = emptyDatabase("histmersion_squash_creator_test")
    Migrate.ensureRole(ds, Migrate.QueryRole)
    val _ = Flyway.configure().configuration(Migrate.flyway(ds).getConfiguration).target("112").load().migrate()
    exec(
      ds,
      """INSERT INTO jobs (id, kind, status, pipeline) VALUES
        |  ('11111111-1111-4111-8111-111111111111', 'agent', 'done', 'review-article'),
        |  ('22222222-2222-4222-8222-222222222222', 'ocr', 'done', NULL)""".stripMargin
    )
    exec(
      ds,
      """INSERT INTO queue_items (id, list, text, topic, status, created_job, updated_at) VALUES
        |  (1, 'tasks', 'review the article', 'agent review', 'open', '11111111-1111-4111-8111-111111111111', '2026-01-01T00:00:00Z'),
        |  (2, 'log', 'converted', NULL, 'open', '22222222-2222-4222-8222-222222222222', '2026-01-01T00:00:00Z'),
        |  (3, 'tasks', 'buy the book', 'Backlog', 'open', NULL, '2026-01-01T00:00:00Z'),
        |  (4, 'acquire', 'gone job', NULL, 'done', 'zz-missing-job', '2026-01-01T00:00:00Z')""".stripMargin
    )
    val before = strings(ds, QueueRowsAtSquash)
    assertEquals(Migrate.run(ds), Latest)
    assertEquals(
      strings(ds, "SELECT id || '|' || creator::text || '|' || actor FROM queue_items ORDER BY id"),
      List(
        """1|{"kind": "automated", "workflow": "review-article"}|agents""",
        """2|{"job": "22222222-2222-4222-8222-222222222222", "kind": "unknown"}|person""",
        """3|{"kind": "unknown"}|person""",
        """4|{"job": "zz-missing-job", "kind": "unknown"}|person"""
      )
    )
    assertEquals(strings(ds, QueueRowsAtSquash), before, "no other column moved, updated_at included")
    assertEquals(
      strings(
        ds,
        "SELECT is_nullable FROM information_schema.columns WHERE table_name = 'queue_items' AND column_name = 'creator'"
      ),
      List("NO")
    )

  test("V115: every record table gets its creator from stored facts only; nothing else of a row moves"):
    val ds = emptyDatabase("histmersion_squash_records_test")
    Migrate.ensureRole(ds, Migrate.QueryRole)
    val _ = Flyway.configure().configuration(Migrate.flyway(ds).getConfiguration).target("113").load().migrate()
    exec(
      ds,
      """INSERT INTO jobs (id, kind, status, pipeline, origin, parent_job) VALUES
        |  ('11111111-1111-4111-8111-111111111111', 'agent', 'done', 'review-article', 'person', NULL),
        |  ('22222222-2222-4222-8222-222222222222', 'agent', 'done', 'enrich-source', 'scheduler', NULL),
        |  ('33333333-3333-4333-8333-333333333333', 'agent', 'done', 'enrich-source', 'manager', NULL),
        |  ('44444444-4444-4444-8444-444444444444', 'agent', 'done', 'compile-article', 'run',
        |   '11111111-1111-4111-8111-111111111111'),
        |  ('55555555-5555-4555-8555-555555555555', 'agent', 'done', NULL, 'ingest', NULL),
        |  ('66666666-6666-4666-8666-666666666666', 'ocr', 'done', NULL, NULL, NULL),
        |  ('77777777-7777-4777-8777-777777777777', 'agent', 'done', 'Not A Name', 'run',
        |   '77777777-7777-4777-8777-777777777777')""".stripMargin
    )
    exec(
      ds,
      """INSERT INTO approvals (id, job_id, kind, proposal) VALUES
        |  (1, '11111111-1111-4111-8111-111111111111', 'status', '{}'),
        |  (2, '66666666-6666-4666-8666-666666666666', 'status', '{}'),
        |  (3, NULL, 'status', '{}')""".stripMargin
    )
    exec(
      ds,
      """INSERT INTO events (id, label, catalogue, updated_at) VALUES
        |  ('zz-ev-1', 'an eruption', 'gvp', '2026-01-01T00:00:00Z'),
        |  ('zz-ev-2', 'a battle', NULL, '2026-01-01T00:00:00Z')""".stripMargin
    )
    exec(
      ds,
      """INSERT INTO places (id, label, kind, import_source, updated_at) VALUES
        |  ('zz-pl-1', 'a site', 'site', 'awmc', '2026-01-01T00:00:00Z'),
        |  ('zz-pl-2', 'a town', 'settlement', NULL, '2026-01-01T00:00:00Z')""".stripMargin
    )
    exec(
      ds,
      """INSERT INTO sources (citekey, title, added_date, updated_at) VALUES
        |  ('zz-src', 'A source', '2025-05-05', '2026-01-01T00:00:00Z')""".stripMargin
    )
    exec(
      ds,
      """INSERT INTO assets (citekey, name, mime, bytes, sha256, updated_at) VALUES
        |  ('zz-src', 'fig1.png', 'image/png', '\x00', 'aa', '2026-01-01T00:00:00Z')""".stripMargin
    )
    exec(
      ds,
      """INSERT INTO assets (provider, provider_key, source_url, retrieved_at, name, mime, bytes, sha256, updated_at)
        |VALUES ('met', 'k1', 'https://example.org/k1', '2026-01-01T00:00:00Z', 'k1.jpg', 'image/jpeg', '\x00', 'bb',
        |        '2026-01-01T00:00:00Z')""".stripMargin
    )
    val updatedBefore = strings(ds, UpdatedAt)
    assertEquals(Migrate.run(ds), Latest)
    assertEquals(
      strings(ds, "SELECT id || '|' || creator::text FROM jobs ORDER BY id"),
      List(
        """11111111-1111-4111-8111-111111111111|{"kind": "manual"}""",
        """22222222-2222-4222-8222-222222222222|{"kind": "automated", "agent": {"kind": "code"}, "plugin": "engine", "surface": "internal", "workflow": "scheduler"}""",
        """33333333-3333-4333-8333-333333333333|{"kind": "automated", "agent": {"kind": "code"}, "plugin": "engine", "surface": "internal", "workflow": "managers"}""",
        """44444444-4444-4444-8444-444444444444|{"kind": "automated", "surface": "internal", "workflow": "review-article"}""",
        """55555555-5555-4555-8555-555555555555|{"kind": "unknown"}""",
        """66666666-6666-4666-8666-666666666666|{"kind": "unknown"}""",
        """77777777-7777-4777-8777-777777777777|{"kind": "unknown"}"""
      )
    )
    assertEquals(
      strings(ds, "SELECT id || '|' || creator::text FROM approvals ORDER BY id"),
      List(
        """1|{"kind": "automated", "surface": "internal", "workflow": "review-article"}""",
        """2|{"kind": "unknown"}""",
        """3|{"kind": "unknown"}"""
      )
    )
    assertEquals(
      strings(ds, "SELECT id || '|' || creator::text FROM events ORDER BY id"),
      List(
        """zz-ev-1|{"kind": "automated", "agent": {"kind": "code"}, "surface": "internal", "workflow": "event-import"}""",
        """zz-ev-2|{"kind": "unknown"}"""
      )
    )
    assertEquals(
      strings(ds, "SELECT id || '|' || creator::text FROM places WHERE id LIKE 'zz-pl-%' ORDER BY id"),
      List(
        """zz-pl-1|{"kind": "automated", "agent": {"kind": "code"}, "surface": "internal", "workflow": "gazetteer-import"}""",
        """zz-pl-2|{"kind": "unknown"}"""
      )
    )
    assertEquals(
      strings(ds, "SELECT name || '|' || creator::text FROM assets ORDER BY name"),
      List(
        """fig1.png|{"kind": "unknown"}""",
        """k1.jpg|{"kind": "automated", "agent": {"kind": "code"}, "surface": "internal", "workflow": "media-import"}"""
      )
    )
    assertEquals(strings(ds, "SELECT creator::text FROM sources"), List("""{"kind": "unknown"}"""))
    assertEquals(
      strings(ds, "SELECT coalesce(created_at::text, 'unknown') FROM events ORDER BY id"),
      List("unknown", "unknown"),
      "an existing row's creation date is not invented"
    )
    assertEquals(strings(ds, UpdatedAt), updatedBefore, "updated_at untouched")
    assertEquals(
      strings(
        ds,
        """SELECT count(*)::text FROM information_schema.columns
          |WHERE table_schema = 'public' AND column_name = 'creator' AND is_nullable = 'NO'
          |  AND data_type = 'jsonb'""".stripMargin
      ),
      List("29")
    )

  test("V116: no record table's creator has a default, so an INSERT that names none fails"):
    val ds = emptyDatabase("histmersion_squash_required_test")
    assertEquals(Migrate.run(ds), Latest)
    assertEquals(
      strings(
        ds,
        """SELECT table_name FROM information_schema.columns
          |WHERE table_schema = 'public' AND column_name = 'creator'
          |  AND (column_default IS NOT NULL OR is_nullable <> 'NO')""".stripMargin
      ),
      Nil
    )
    val refused =
      try
        exec(ds, "INSERT INTO sources (citekey, title) VALUES ('zz-nobody', 'Made by nobody')")
        None
      catch case e: java.sql.SQLException => Some(e.getSQLState)
    assertEquals(refused, Some("23502"), "not_null_violation")
