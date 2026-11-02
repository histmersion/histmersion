package com.histmersion.db

import com.histmersion.util.database.Transactor

import com.histmersion.domain.automation.approvals.ApprovalQuery

import com.histmersion.util.jobs.JobRepo

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.{ApprovalKind, ApprovalState, RunState}
import com.histmersion.api.automation.ListFilters.DecidedBy

import com.augustnagro.magnum.*

import java.time.Instant
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.util.database.Migrate
import com.histmersion.api.automation.Agents
import com.histmersion.util.database.given

/** The server-side filters of the approvals, agent-runs and jobs lists and their facet counts (core `ListFilters`)
  * against a real Postgres (HISTMERSION_TEST_DB_URL; skipped otherwise). Other suites write the same tables at the same
  * time, so every fixture carries this run's tag in what the text filter (`subject`) matches, and every assertion is
  * made under that filter.
  */
class ListFilterRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.copy(url = testDb.get))
    Migrate.run(d); d

  private val tag = s"zzlf${System.nanoTime().toHexString.takeRight(8)}"

  override def afterSuite(): Unit =
    if testDb.nonEmpty then
      transact(ds):
        sql"DELETE FROM approvals WHERE subject LIKE ${tag + "%"} OR citekey LIKE ${tag + "%"}".update.run()
        sql"DELETE FROM jobs WHERE id LIKE ${tag + "%"}".update.run()
      ()

  private def approval(
      kind: String,
      state: String,
      subject: Option[String],
      citekey: Option[String] = None,
      note: Option[String] = None,
      result: Option[String] = None,
      job: Option[String] = None
  ): Long =
    transact(ds):
      sql"""INSERT INTO approvals (job_id, kind, citekey, subject, proposal, state, note, result, decided_at)
            VALUES ($job, $kind, $citekey, $subject, '{}'::jsonb, $state, $note, $result,
                    ${Option.when(state != "open")("2026-10-01T00:00:00Z")}::timestamptz)
            RETURNING id""".query[Long].run().head

  private def job(
      id: String,
      kind: String,
      status: String,
      at: String,
      citekey: Option[String] = None,
      pipeline: Option[String] = None,
      target: Option[String] = None,
      goal: Option[String] = None,
      manager: Option[String] = None,
      origin: Option[String] = None
  ): String =
    transact(ds):
      sql"""INSERT INTO jobs (id, kind, status, created_at, citekey, pipeline, target_id, goal_slug, manager_id, origin)
            VALUES ($id, $kind, $status, $at::timestamptz, $citekey, $pipeline, $target, $goal, $manager, $origin)""".update
        .run()
    id

  private def step(jobId: String, seq: Int, model: String): Unit =
    transact(ds):
      sql"""INSERT INTO agent_steps (job_id, seq, attempt, name, kind, status, model)
            VALUES ($jobId, $seq, 1, 'x', 'llm', 'done', $model)""".update.run()
    ()

  test(
    "approvals: kind, state, subject, goal and decider filter on the server; each facet is counted under the others"
  ):
    val g = s"$tag-goal"
    val run = job(s"$tag-run-a", Agents.JobKind, RunState.Done.wire, "2026-10-01T00:00:00Z", goal = Some(g))
    val gp1 = approval("goal-plan", "open", Some(g))
    val gp2 =
      approval("goal-plan", "approved", Some(s"$tag-other"), note = Some(s"${DecidedBy.AutopilotNote} approved — fine"))
    val pub = approval("article-publish", "open", Some(s"$tag-article"), job = Some(run))
    val edg = approval(
      "edge",
      "approved",
      None,
      citekey = Some(s"${tag}_src"),
      result = Some(s"${DecidedBy.AutoResult}: added")
    )
    val rej = approval("edge", "rejected", None, citekey = Some(s"${tag}_src2"), note = Some("not the critic"))
    def ids(q: ApprovalQuery) =
      connect(ds)(AgentRepo.approvalPage(q, 50, None, None)).toOption.get._1.map(_.id).toSet
    val mine = ApprovalQuery(subject = Some(tag))
    assertEquals(ids(mine), Set(gp1, gp2, pub, edg, rej))
    assertEquals(ids(mine.copy(state = Some(ApprovalState.Open))), Set(gp1, pub))
    assertEquals(ids(mine.copy(kinds = List(ApprovalKind.Edge, ApprovalKind.GoalPlan))), Set(gp1, gp2, edg, rej))
    // goal: its own plan, and what a run for it proposed
    assertEquals(ids(ApprovalQuery(goal = Some(g))), Set(gp1, pub))
    // the decider
    assertEquals(ids(mine.copy(decidedBy = List(DecidedBy.Autopilot))), Set(gp2))
    assertEquals(ids(mine.copy(decidedBy = List(DecidedBy.Auto))), Set(edg))
    assertEquals(ids(mine.copy(decidedBy = List(DecidedBy.Person))), Set(rej))
    // a text filter takes `%` and `_` literally
    assertEquals(ids(ApprovalQuery(subject = Some(s"$tag%"))), Set.empty[Long])
    // the counts: per state over the kind filter; per kind over the state filter, not the kind filter
    val (states, facets) =
      connect(ds)(
        AgentRepo.approvalFacets(mine.copy(state = Some(ApprovalState.Open), kinds = List(ApprovalKind.GoalPlan)))
      )
    assertEquals(states.toMap, Map("open" -> 1, "approved" -> 1))
    assertEquals(facets.toMap.apply("kind").toMap, Map("goal-plan" -> 1, "article-publish" -> 1))
    val (_, every) = connect(ds)(AgentRepo.approvalFacets(mine))
    assertEquals(every.toMap.apply("kind"), List("edge" -> 2, "goal-plan" -> 2, "article-publish" -> 1))
    assertEquals(every.toMap.apply("decidedBy").toMap, Map("autopilot" -> 1, "auto" -> 1, "person" -> 1))

  test("agent runs: pipeline, status, manager, origin, model, provider, dates and target text; the facets"):
    val t = s"$tag-t"
    val a = job(
      s"$tag-r1",
      Agents.JobKind,
      "done",
      "2026-09-01T10:00:00Z",
      pipeline = Some("review-article"),
      target = Some(s"$t-one"),
      manager = Some("review"),
      origin = Some("manager")
    )
    val b = job(
      s"$tag-r2",
      Agents.JobKind,
      "failed",
      "2026-09-15T10:00:00Z",
      pipeline = Some("enrich-source"),
      target = Some(s"$t-two"),
      origin = None
    )
    val c = job(
      s"$tag-r3",
      Agents.JobKind,
      "done",
      "2026-10-01T10:00:00Z",
      pipeline = Some("review-article"),
      target = Some(s"$t-three"),
      manager = Some("seed:x"),
      origin = Some("manager")
    )
    // an OCR job is not a run, whatever it is about
    job(s"$tag-o1", "ingest", "done", "2026-10-01T10:00:00Z", citekey = Some(s"$t-four"))
    step(a, 1, "codex:gpt-a"); step(a, 2, "codex:gpt-b"); step(b, 1, "claude-code:opus")
    def ids(q: com.histmersion.domain.automation.agents.RunQuery) =
      connect(ds)(AgentRepo.page(q, 50, None, None)).toOption.get._1.map(_.id).toSet
    val mine = com.histmersion.domain.automation.agents.RunQuery(q = Some(t))
    assertEquals(ids(mine), Set(a, b, c))
    assertEquals(ids(mine.copy(pipelines = List("review-article"))), Set(a, c))
    assertEquals(ids(mine.copy(status = List("failed", "cancelled"))), Set(b))
    assertEquals(ids(mine.copy(managers = List("seed:x"))), Set(c))
    // a run with no recorded origin was a person's
    assertEquals(ids(mine.copy(origins = List("person"))), Set(b))
    assertEquals(ids(mine.copy(models = List("codex:gpt-b"))), Set(a))
    assertEquals(ids(mine.copy(providers = List("claude-code"))), Set(b))
    val at = (s: String) => Some(Instant.parse(s))
    assertEquals(ids(mine.copy(since = at("2026-09-10T00:00:00Z"))), Set(b, c))
    assertEquals(ids(mine.copy(since = at("2026-09-10T00:00:00Z"), until = at("2026-10-01T00:00:00Z"))), Set(b))
    val (states, facets) = connect(ds)(AgentRepo.runFacets(mine.copy(pipelines = List("review-article"))))
    assertEquals(states.toMap, Map("done" -> 2))
    val f = facets.toMap
    assertEquals(f("pipeline"), List("review-article" -> 2, "enrich-source" -> 1))
    assertEquals(f("manager").toMap, Map("review" -> 1, "seed:x" -> 1))
    assertEquals(f("model").toMap, Map("codex:gpt-a" -> 1, "codex:gpt-b" -> 1))
    // two codex models on one run count it once for the provider
    assertEquals(f("provider"), List("codex" -> 1))
    assertEquals(
      connect(ds)(AgentRepo.runFacets(mine))._2.toMap.apply("origin").toMap,
      Map("manager" -> 2, "person" -> 1)
    )

  test("jobs: kind, status, dates and subject text; the kind facet counts over the state filter"):
    val t = s"$tag-j"
    val i1 = job(s"$tag-j1", "ingest", "done", "2026-09-01T00:00:00Z", citekey = Some(s"${t}_a"))
    val i2 = job(s"$tag-j2", "ingest", "failed", "2026-09-02T00:00:00Z", citekey = Some(s"${t}_b"))
    val m1 = job(s"$tag-j3", "media-import", "done", "2026-09-03T00:00:00Z", citekey = Some(s"${t}_c"))
    val r1 = job(
      s"$tag-j4",
      Agents.JobKind,
      "done",
      "2026-09-04T00:00:00Z",
      pipeline = Some("enrich-source"),
      target = Some(s"${t}_d")
    )
    def ids(q: com.histmersion.util.jobs.JobQuery) =
      Transactor(ds).read(JobRepo.page(q, 50, None, None)).toOption.get._1.map(_.id).toSet
    val mine = com.histmersion.util.jobs.JobQuery(q = Some(t))
    assertEquals(ids(mine), Set(i1, i2, m1, r1))
    assertEquals(ids(mine.copy(kinds = List("ingest", "media-import"))), Set(i1, i2, m1))
    assertEquals(ids(mine.copy(status = List("failed"))), Set(i2))
    assertEquals(ids(mine.copy(until = Some(Instant.parse("2026-09-03T00:00:00Z")))), Set(i1, i2))
    // the start of an id finds that job
    assertEquals(ids(com.histmersion.util.jobs.JobQuery(q = Some(s"$tag-j3"))), Set(m1))
    val (states, facets) =
      Transactor(ds).read(JobRepo.facets(mine.copy(kinds = List("ingest"), status = List("done")), 40))
    assertEquals(states.toMap, Map("done" -> 1, "failed" -> 1))
    assertEquals(facets.toMap.apply("kind").toMap, Map("ingest" -> 1, "media-import" -> 1, "agent" -> 1))
    // the old single-state page still reads through the same query
    assert(Transactor(ds).read(JobRepo.page(Some("failed"), 500)).toOption.get._1.exists(_.id == i2))
