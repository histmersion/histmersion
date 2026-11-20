package com.histmersion
package domain.automation.agents
import com.histmersion.util.problem.Text.sentence
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.automation.{EngineStores, EngineStoreSuite, Run}
import com.histmersion.util.jobs.{Job, RunRow}

import java.time.Instant
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.managers.ManagerLight
import com.histmersion.util.database.TestPermits.given

/** The contract the runs' reads and steps keep: a run's usage is its steps' sum; attempts, checkpoints and the trace in
  * their order; the lists and pages, their filters and facets.
  */
abstract class RunServiceContract extends EngineStoreSuite:
  private def r(s: EngineStores) = s.agentRuns
  private val started = Instant.parse("2026-10-31T10:00:00Z")
  private def step(
      seq: AgentRepo.StepRecord.Seq,
      attempt: AgentRepo.StepRecord.Attempt,
      status: StepStatus,
      model: AgentRepo.StepRecord.Model,
      cost: BigDecimal,
      output: Option[AgentRepo.StepRecord.Output]
  ) =
    AgentRepo.StepRecord(
      seq,
      attempt,
      AgentRepo.StepRecord.Name(s"step$seq"),
      StepKind.Llm,
      status,
      output,
      Some(AgentRepo.StepRecord.PromptHash("h")),
      Some(model),
      Usage(Usage.Input(10L * attempt.value), Usage.Output(2), Usage.CacheRead(1), Usage.CacheWrite(0)),
      cost,
      Option.when(status == StepStatus.Failed)(Problem.Unclassified(Legacy.Text("boom"))),
      started,
      Some(AgentRepo.StepRecord.ActualModel(model.value)),
      List(WouldWrite(WouldWrite.What("w"), WouldWrite.Payload("p"))),
      Some(AgentRepo.StepRecord.FirstCallTokens(7L)),
      Some(AgentRepo.StepRecord.Turns(attempt.value))
    )
  private def jobOf(id: Run.Id): Job.Id = Job.Id.assume(id.value)
  private def move(s: EngineStores, id: Run.Id, from: RunRow.Status, to: RunRow.Status) =
    assert(s.runs.move(Job.Kind.assume(JobKind), id, from, to, None, None, None), s"$id $from → $to")

  stest("a run reads back with its steps' usage summed; attempts, checkpoints and the trace in their order") { s =>
    val runId = s.run(RunRow.Target("zz2020_a"), goal = Some(RunRow.Goal("zz-g")))
    val id = jobOf(runId)
    val fresh = r(s).get(runId).get
    assertEquals(
      (fresh.pipeline, fresh.target, fresh.status, fresh.usage, fresh.costUsd, fresh.goal),
      ("enrich-source", "zz2020_a", "queued", Usage.Zero, 0.0, Some("zz-g"))
    )
    assertEquals(
      (r(s).attempts(id, AgentRepo.StepRecord.Seq(1)), r(s).checkpoint(id, AgentRepo.StepRecord.Seq(1))),
      (0, None)
    )
    r(s).record(
      id,
      step(
        AgentRepo.StepRecord.Seq(1),
        AgentRepo.StepRecord.Attempt(1),
        StepStatus.Failed,
        AgentRepo.StepRecord.Model("openai:gpt-5"),
        BigDecimal("0.10"),
        None
      ),
      List(
        AgentRepo.MessageRecord(
          AgentRepo.MessageRecord.N(1),
          AgentRepo.MessageRecord.Role("user"),
          Some(AgentRepo.MessageRecord.Content("hi")),
          None,
          None,
          None,
          None
        )
      )
    )
    assertEquals(
      (r(s).attempts(id, AgentRepo.StepRecord.Seq(1)), r(s).checkpoint(id, AgentRepo.StepRecord.Seq(1))),
      (1, None)
    )
    r(s).record(
      id,
      step(
        AgentRepo.StepRecord.Seq(1),
        AgentRepo.StepRecord.Attempt(2),
        StepStatus.Done,
        AgentRepo.StepRecord.Model("anthropic:claude"),
        BigDecimal("0.25"),
        Some(AgentRepo.StepRecord.Output("\"abcdef\""))
      ),
      List(
        AgentRepo.MessageRecord(
          AgentRepo.MessageRecord.N(2),
          AgentRepo.MessageRecord.Role("assistant"),
          Some(AgentRepo.MessageRecord.Content("done")),
          None,
          None,
          None,
          Some(AgentRepo.MessageRecord.LatencyMs(5))
        ),
        AgentRepo.MessageRecord(
          AgentRepo.MessageRecord.N(1),
          AgentRepo.MessageRecord.Role("user"),
          Some(AgentRepo.MessageRecord.Content("again")),
          Some(AgentRepo.MessageRecord.ToolName("t")),
          Some(AgentRepo.MessageRecord.ToolInput("{}")),
          Some(AgentRepo.MessageRecord.ToolOutput("r")),
          None
        )
      )
    )
    assertEquals(r(s).attempts(id, AgentRepo.StepRecord.Seq(1)), 2)
    assertEquals(r(s).checkpoint(id, AgentRepo.StepRecord.Seq(1)), Some(("step1", "\"abcdef\"")))
    val run = r(s).get(runId).get
    assertEquals(
      (run.usage, run.costUsd),
      (Usage(Usage.Input(30), Usage.Output(4), Usage.CacheRead(2), Usage.CacheWrite(0)), 0.35)
    )
    assertEquals(
      r(s).runSpend(id),
      (Usage(Usage.Input(30), Usage.Output(4), Usage.CacheRead(2), Usage.CacheWrite(0)), BigDecimal("0.35"))
    )
    val steps = r(s).steps(id, AgentRepo.OutputChars(3))
    assertEquals(
      steps.map(x => (x.seq, x.attempt, x.status, x.output, x.error.map(_.sentence))),
      List((1, 1, "failed", None, Some("boom")), (1, 2, "done", Some("\"ab"), None))
    )
    assertEquals(steps.head.wouldWrite, List(WouldWrite(WouldWrite.What("w"), WouldWrite.Payload("p"))))
    assertEquals(
      (steps.head.firstCallTokens, steps.last.turns, steps.head.startedAt),
      (Some(7L), Some(2), "2026-10-31T10:00:00Z")
    )
    assertEquals(
      r(s).messages(id).map(m => (m.seq, m.n, m.role)),
      List((1, 1, "user"), (1, 1, "user"), (1, 2, "assistant"))
    )
    val (day, month) = r(s).spendDayMonth()
    assert(day >= BigDecimal("0.35") && month >= day, (day, month).toString)
    assertEquals(r(s).pruneMessages(RunService.RetentionDays(0)), 0)
    assertEquals(r(s).pruneMessages(RunService.RetentionDays(1)), 0, "nothing a day old")
  }

  stest(
    "lists: newest first by state and goal; a target's and a manager's runs; the unsettled ones; counts per state"
  ) { s =>
    val a = s.run(RunRow.Target("zz2020_a"), goal = Some(RunRow.Goal("zz-g")))
    val b = s.run(
      RunRow.Target("zz2020_b"),
      manager = Some(RunRow.ManagerId("watch:zz-g")),
      item = Some(RunRow.ManagerItem(1L))
    )
    val c = s.run(
      RunRow.Target("zz2020_a"),
      manager = Some(RunRow.ManagerId("watch:zz-g")),
      pipeline = RunRow.Pipeline("currency-check")
    )
    move(s, b, RunRow.Status("queued"), RunRow.Status("running"))
    move(s, b, RunRow.Status("running"), RunRow.Status("done"))
    assertEquals(r(s).list(None, RunQuery.Limit(10)).map(_.id.value: String), Vector(c, b, a))
    assertEquals(r(s).list(Some(RunQuery.Status(" QUEUED ")), RunQuery.Limit(10)).map(_.id.value: String), Vector(c, a))
    assertEquals(r(s).list(None, RunQuery.Limit(10), Some(Goal.Slug("zz-g"))).map(_.id.value: String), Vector(a))
    assertEquals(r(s).list(None, RunQuery.Limit(1)).map(_.id.value: String), Vector(c))
    assertEquals(
      r(s).forTarget(Pipeline.EnrichSource, AgentsControllers.Target("zz2020_a")).map(_.id.value: String),
      Vector(a)
    )
    assertEquals(r(s).forManager(ManagerLight.Id("watch:zz-g")).map(_.id.value: String), Vector(c, b))
    assertEquals(
      r(s).forManager(ManagerLight.Id("watch:zz-g"), active = RunService.Active(true)).map(_.id.value: String),
      Vector(c)
    )
    assertEquals(
      r(s)
        .forManager(com.histmersion.domain.automation.managers.ManagerLight.Id("watch:zz-g"), limit = RunQuery.Limit(1))
        .map(_.id.value: String),
      Vector(c)
    )
    assertEquals(r(s).unsettledOn(AgentsControllers.Target("zz2020_a")).map(_.id.value: String).toSet, Set(a, c))
    assertEquals(r(s).unsettledOn(AgentsControllers.Target("zz2020_b")), Vector.empty)
    assertEquals(r(s).stateCounts(), List("queued" -> 2, "done" -> 1))
    assertEquals(r(s).stateCounts(Some(Goal.Slug("zz-g"))), List("queued" -> 1))
    assertEquals(r(s).get(b).flatMap(_.managerItem), Some(1L))
  }

  stest(
    "pages newest first with cursors; filters by state, pipeline, manager, goal, model (a step's too), provider, text; facets"
  ) { s =>
    val a = s.run(RunRow.Target("zz2020_tin"), goal = Some(RunRow.Goal("zz-g")))
    val b = s.run(RunRow.Target("zz2020_copper"), manager = Some(RunRow.ManagerId("watch:zz-g")))
    val c = s.run(RunRow.Target("zz2020_tin_b"), pipeline = RunRow.Pipeline("currency-check"))
    r(s).record(
      jobOf(b),
      step(
        AgentRepo.StepRecord.Seq(1),
        AgentRepo.StepRecord.Attempt(1),
        StepStatus.Done,
        AgentRepo.StepRecord.Model("openai:gpt-5"),
        BigDecimal("0.01"),
        Some(AgentRepo.StepRecord.Output("1"))
      ),
      Nil
    )
    move(s, c, RunRow.Status("queued"), RunRow.Status("running"))
    val Right((first, more)) = r(s).page(None, RunQuery.Limit(2)): @unchecked
    assertEquals((first.map(_.id.value: String), more), (Vector(c, b), true))
    val Right((rest, none)) = r(s).page(
      None,
      com.histmersion.domain.automation.agents.RunQuery.Limit(2),
      before = Some(AgentRepo.PageCursor(b.value))
    ): @unchecked
    assertEquals((rest.map(_.id.value: String), none), (Vector(a), false))
    val Right((upTo, older)) = r(s).page(
      None,
      com.histmersion.domain.automation.agents.RunQuery.Limit(5),
      upTo = Some(RunQuery.UpTo(b.value))
    ): @unchecked
    assertEquals((upTo.map(_.id.value: String), older), (Vector(c, b), true))
    assert(
      r(s)
        .page(
          None,
          com.histmersion.domain.automation.agents.RunQuery.Limit(2),
          before = Some(AgentRepo.PageCursor("00000000-0000-0000-0000-000000000000"))
        )
        .left
        .exists(_.isNotFound)
    )
    def page(q: RunQuery) = r(s).page(q, RunQuery.Limit(10), None, None).map(_._1.map(_.id.value: String))
    assertEquals(page(RunQuery(status = List(RunQuery.Status("running")))), Right(Vector(c)))
    assertEquals(page(RunQuery(pipelines = List(RunQuery.Pipelines("currency-check")))), Right(Vector(c)))
    assertEquals(page(RunQuery(managers = List(RunQuery.Managers("watch:zz-g")))), Right(Vector(b)))
    assertEquals(page(RunQuery(goal = Some(RunQuery.Goal("zz-g")))), Right(Vector(a)))
    assertEquals(
      page(RunQuery(models = List(RunQuery.Models("openai:gpt-5")))),
      Right(Vector(b)),
      "a model one of its steps used"
    )
    assertEquals(page(RunQuery(providers = List(RunQuery.Providers("local")))), Right(Vector(c, b, a)))
    assertEquals(page(RunQuery(providers = List(RunQuery.Providers("openai")))), Right(Vector(b)))
    assertEquals(page(RunQuery(q = Some(RunQuery.Q("TIN")))), Right(Vector(c, a)))
    assertEquals(page(RunQuery(origins = List(RunQuery.Origins("person")))), Right(Vector(c, b, a)))
    assertEquals(page(RunQuery(since = Some(Instant.parse("2099-01-01T00:00:00Z")))), Right(Vector.empty))
    val (states, facets) = r(s).runFacets(RunQuery(status = List(RunQuery.Status("queued"))))
    assertEquals(states, List("queued" -> 2, "running" -> 1))
    val f = facets.toMap
    assertEquals(f("pipeline"), List("enrich-source" -> 2))
    assertEquals(f("manager"), List("watch:zz-g" -> 1))
    assertEquals(f("model"), List("local:scripted" -> 2, "openai:gpt-5" -> 1))
    assertEquals(f("provider"), List("local" -> 2, "openai" -> 1))
    assertEquals(f("origin"), List("person" -> 2))
  }
