package com.histmersion.domain.automation.agents

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.automation.{EngineStores, EngineStoreSuite}

import java.time.Instant

/** The contract the runs' reads and steps keep: a run's usage is its steps' sum; attempts, checkpoints and the trace in
  * their order; the lists and pages, their filters and facets.
  */
abstract class RunServiceContract extends EngineStoreSuite:
  private def r(s: EngineStores) = s.agentRuns
  private val started = Instant.parse("2026-10-31T10:00:00Z")
  private def step(seq: Int, attempt: Int, status: StepStatus, model: String, cost: String, output: Option[String]) =
    AgentRepo.StepRecord(
      seq,
      attempt,
      s"step$seq",
      StepKind.Llm,
      status,
      output,
      Some(com.histmersion.domain.automation.agents.AgentRepo.StepRecord.PromptHash("h")),
      Some(model),
      Usage(10L * attempt, com.histmersion.api.automation.Agents.Usage.Output(2), com.histmersion.api.automation.Agents.Usage.CacheRead(1), com.histmersion.api.automation.Agents.Usage.CacheWrite(0)),
      BigDecimal(cost),
      Option.when(status == StepStatus.Failed)(com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Error("boom")),
      started,
      Some(model),
      List(WouldWrite(com.histmersion.api.automation.Agents.WouldWrite.What("w"), com.histmersion.api.automation.Agents.WouldWrite.Payload("p"))),
      Some(com.histmersion.domain.automation.agents.AgentRepo.StepRecord.FirstCallTokens(7L)),
      Some(attempt)
    )
  private def move(s: EngineStores, id: String, from: String, to: String) =
    assert(s.runs.move(JobKind, id, from, to, None, None, None), s"$id $from → $to")

  stest("a run reads back with its steps' usage summed; attempts, checkpoints and the trace in their order") { s =>
    val id = s.run("zz2020_a", goal = Some("zz-g"))
    val fresh = r(s).get(id).get
    assertEquals(
      (fresh.pipeline, fresh.target, fresh.status, fresh.usage, fresh.costUsd, fresh.goal),
      ("enrich-source", "zz2020_a", "queued", Usage.Zero, 0.0, Some("zz-g"))
    )
    assertEquals((r(s).attempts(id, com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Seq(1)), r(s).checkpoint(id, com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Seq(1))), (0, None))
    r(s).record(
      id,
      step(1, 1, StepStatus.Failed, "openai:gpt-5", "0.10", None),
      List(AgentRepo.MessageRecord(com.histmersion.domain.automation.agents.AgentRepo.MessageRecord.N(1), com.histmersion.domain.automation.agents.AgentRepo.MessageRecord.Role("user"), Some(com.histmersion.domain.automation.agents.AgentRepo.MessageRecord.Content("hi")), None, None, None, None))
    )
    assertEquals((r(s).attempts(id, com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Seq(1)), r(s).checkpoint(id, com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Seq(1))), (1, None))
    r(s).record(
      id,
      step(1, 2, StepStatus.Done, "anthropic:claude", "0.25", Some("\"abcdef\"")),
      List(
        AgentRepo.MessageRecord(com.histmersion.domain.automation.agents.AgentRepo.MessageRecord.N(2), com.histmersion.domain.automation.agents.AgentRepo.MessageRecord.Role("assistant"), Some(com.histmersion.domain.automation.agents.AgentRepo.MessageRecord.Content("done")), None, None, None, Some(com.histmersion.domain.automation.agents.AgentRepo.MessageRecord.LatencyMs(5))),
        AgentRepo.MessageRecord(com.histmersion.domain.automation.agents.AgentRepo.MessageRecord.N(1), com.histmersion.domain.automation.agents.AgentRepo.MessageRecord.Role("user"), Some(com.histmersion.domain.automation.agents.AgentRepo.MessageRecord.Content("again")), Some(com.histmersion.domain.automation.agents.AgentRepo.MessageRecord.ToolName("t")), Some(com.histmersion.domain.automation.agents.AgentRepo.MessageRecord.ToolInput("{}")), Some(com.histmersion.domain.automation.agents.AgentRepo.MessageRecord.ToolResult("r")), None)
      )
    )
    assertEquals(r(s).attempts(id, com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Seq(1)), 2)
    assertEquals(r(s).checkpoint(id, com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Seq(1)), Some(("step1", "\"abcdef\"")))
    val run = r(s).get(id).get
    assertEquals((run.usage, run.costUsd), (Usage(com.histmersion.api.automation.Agents.Usage.Input(30), com.histmersion.api.automation.Agents.Usage.Output(4), com.histmersion.api.automation.Agents.Usage.CacheRead(2), com.histmersion.api.automation.Agents.Usage.CacheWrite(0)), 0.35))
    assertEquals(r(s).runSpend(id), (Usage(com.histmersion.api.automation.Agents.Usage.Input(30), com.histmersion.api.automation.Agents.Usage.Output(4), com.histmersion.api.automation.Agents.Usage.CacheRead(2), com.histmersion.api.automation.Agents.Usage.CacheWrite(0)), BigDecimal("0.35")))
    val steps = r(s).steps(id, com.histmersion.domain.automation.agents.AgentRepo.OutputChars(3))
    assertEquals(
      steps.map(x => (x.seq, x.attempt, x.status, x.output, x.error)),
      List((1, 1, "failed", None, Some("boom")), (1, 2, "done", Some("\"ab"), None))
    )
    assertEquals(steps.head.wouldWrite, List(WouldWrite(com.histmersion.api.automation.Agents.WouldWrite.What("w"), com.histmersion.api.automation.Agents.WouldWrite.Payload("p"))))
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
    assertEquals(r(s).pruneMessages(com.histmersion.domain.automation.agents.RunService.RetentionDays(0)), 0)
    assertEquals(r(s).pruneMessages(com.histmersion.domain.automation.agents.RunService.RetentionDays(1)), 0, "nothing a day old")
  }

  stest(
    "lists: newest first by state and goal; a target's and a manager's runs; the unsettled ones; counts per state"
  ) { s =>
    val a = s.run("zz2020_a", goal = Some("zz-g"))
    val b = s.run("zz2020_b", manager = Some("watch:zz-g"), item = Some(1L))
    val c = s.run("zz2020_a", manager = Some("watch:zz-g"), pipeline = "currency-check")
    move(s, b, "queued", "running")
    move(s, b, "running", "done")
    assertEquals(r(s).list(None, com.histmersion.domain.automation.agents.RunQuery.Limit(10)).map(_.id), Vector(c, b, a))
    assertEquals(r(s).list(Some(com.histmersion.domain.automation.agents.RunQuery.Status(" QUEUED ")), com.histmersion.domain.automation.agents.RunQuery.Limit(10)).map(_.id), Vector(c, a))
    assertEquals(r(s).list(None, com.histmersion.domain.automation.agents.RunQuery.Limit(10), Some(com.histmersion.domain.automation.Goal.Slug("zz-g"))).map(_.id), Vector(a))
    assertEquals(r(s).list(None, com.histmersion.domain.automation.agents.RunQuery.Limit(1)).map(_.id), Vector(c))
    assertEquals(r(s).forTarget(Pipeline.EnrichSource, com.histmersion.domain.automation.agents.AgentsControllers.Target("zz2020_a")).map(_.id), Vector(a))
    assertEquals(r(s).forManager(com.histmersion.domain.automation.managers.ManagerLight.Id("watch:zz-g")).map(_.id), Vector(c, b))
    assertEquals(r(s).forManager(com.histmersion.domain.automation.managers.ManagerLight.Id("watch:zz-g"), active = com.histmersion.domain.automation.agents.RunService.Active(true)).map(_.id), Vector(c))
    assertEquals(r(s).forManager(com.histmersion.domain.automation.managers.ManagerLight.Id("watch:zz-g"), limit = com.histmersion.domain.automation.agents.RunQuery.Limit(1)).map(_.id), Vector(c))
    assertEquals(r(s).unsettledOn(com.histmersion.domain.automation.agents.AgentsControllers.Target("zz2020_a")).map(_.id).toSet, Set(a, c))
    assertEquals(r(s).unsettledOn(com.histmersion.domain.automation.agents.AgentsControllers.Target("zz2020_b")), Vector.empty)
    assertEquals(r(s).stateCounts(), List("queued" -> 2, "done" -> 1))
    assertEquals(r(s).stateCounts(Some(com.histmersion.domain.automation.Goal.Slug("zz-g"))), List("queued" -> 1))
    assertEquals(r(s).get(b).flatMap(_.managerItem), Some(1L))
  }

  stest(
    "pages newest first with cursors; filters by state, pipeline, manager, goal, model (a step's too), provider, text; facets"
  ) { s =>
    val a = s.run("zz2020_tin", goal = Some("zz-g"))
    val b = s.run("zz2020_copper", manager = Some("watch:zz-g"))
    val c = s.run("zz2020_tin_b", pipeline = "currency-check")
    r(s).record(b, step(1, 1, StepStatus.Done, "openai:gpt-5", "0.01", Some("1")), Nil)
    move(s, c, "queued", "running")
    val Right((first, more)) = r(s).page(None, com.histmersion.domain.automation.agents.RunQuery.Limit(2)): @unchecked
    assertEquals((first.map(_.id), more), (Vector(c, b), true))
    val Right((rest, none)) = r(s).page(None, com.histmersion.domain.automation.agents.RunQuery.Limit(2), before = Some(b)): @unchecked
    assertEquals((rest.map(_.id), none), (Vector(a), false))
    val Right((upTo, older)) = r(s).page(None, com.histmersion.domain.automation.agents.RunQuery.Limit(5), upTo = Some(b)): @unchecked
    assertEquals((upTo.map(_.id), older), (Vector(c, b), true))
    assert(
      r(s).page(None, com.histmersion.domain.automation.agents.RunQuery.Limit(2), before = Some(com.histmersion.domain.automation.agents.AgentRepo.PageCursor("00000000-0000-0000-0000-000000000000"))).left.exists(_.contains("No agent run"))
    )
    def page(q: RunQuery) = r(s).page(q, 10, None, None).map(_._1.map(_.id))
    assertEquals(page(RunQuery(status = List(com.histmersion.domain.automation.agents.RunQuery.Status("running")))), Right(Vector(c)))
    assertEquals(page(RunQuery(pipelines = List(com.histmersion.domain.automation.agents.RunQuery.Pipelines("currency-check")))), Right(Vector(c)))
    assertEquals(page(RunQuery(managers = List(com.histmersion.domain.automation.agents.RunQuery.Managers("watch:zz-g")))), Right(Vector(b)))
    assertEquals(page(RunQuery(goal = Some(com.histmersion.domain.automation.agents.RunQuery.Goal("zz-g")))), Right(Vector(a)))
    assertEquals(page(RunQuery(models = List(com.histmersion.domain.automation.agents.RunQuery.Models("openai:gpt-5")))), Right(Vector(b)), "a model one of its steps used")
    assertEquals(page(RunQuery(providers = List(com.histmersion.domain.automation.agents.RunQuery.Providers("local")))), Right(Vector(c, b, a)))
    assertEquals(page(RunQuery(providers = List(com.histmersion.domain.automation.agents.RunQuery.Providers("openai")))), Right(Vector(b)))
    assertEquals(page(RunQuery(q = Some(com.histmersion.domain.automation.agents.RunQuery.Q("TIN")))), Right(Vector(c, a)))
    assertEquals(page(RunQuery(origins = List(com.histmersion.domain.automation.agents.RunQuery.Origins("person")))), Right(Vector(c, b, a)))
    assertEquals(page(RunQuery(since = Some(Instant.parse("2099-01-01T00:00:00Z")))), Right(Vector.empty))
    val (states, facets) = r(s).runFacets(RunQuery(status = List(com.histmersion.domain.automation.agents.RunQuery.Status("queued"))))
    assertEquals(states, List("queued" -> 2, "running" -> 1))
    val f = facets.toMap
    assertEquals(f("pipeline"), List("enrich-source" -> 2))
    assertEquals(f("manager"), List("watch:zz-g" -> 1))
    assertEquals(f("model"), List("local:scripted" -> 2, "openai:gpt-5" -> 1))
    assertEquals(f("provider"), List("local" -> 2, "openai" -> 1))
    assertEquals(f("origin"), List("person" -> 2))
  }
