package com.histmersion.core

import com.histmersion.core.Agents.*

class AgentsSuite extends munit.FunSuite:

  test("ModelRef: only the first colon separates the provider (Ollama ids carry colons)"):
    assertEquals(ModelRef.parse("local:qwen2.5-coder:32b"), Right(ModelRef(ProviderKind.Local, "qwen2.5-coder:32b")))
    assertEquals(ModelRef.parse(" anthropic:claude-x "), Right(ModelRef(ProviderKind.Anthropic, "claude-x")))
    assertEquals(ModelRef.parse("openai:gpt").map(_.wire), Right("openai:gpt"))

  test("ModelRef: unknown provider, no model, no colon are errors"):
    assert(ModelRef.parse("ollama:qwen").left.exists(_.contains("unknown provider 'ollama'")))
    assert(ModelRef.parse("local:").left.exists(_.contains("names no model")))
    assert(ModelRef.parse("qwen").left.exists(_.contains("provider:model")))

  test("Prices: two or four numbers per model, ; separated"):
    val p = Prices.parse("qwen2.5-coder:32b=0/0; claude-x=3/15/0.3/3.75").toOption.get
    assertEquals(p("qwen2.5-coder:32b"), Price(BigDecimal(0), BigDecimal(0)))
    assertEquals(p("claude-x"), Price(BigDecimal(3), BigDecimal(15), Some(BigDecimal("0.3")), Some(BigDecimal("3.75"))))
    assertEquals(Prices.parse("  "), Right(Map.empty))

  test("Prices: every malformed entry is reported, none silently skipped"):
    val e = Prices.parse("a=1; b=x/2; c=1/2/3; =1/1").left.toOption.get
    List("'a=1'", "'b=x/2'", "'c=1/2/3'", "'=1/1'").foreach(m => assert(e.contains(m), e))
    assert(Prices.parse("a=-1/2").isLeft)

  test("cost: per million tokens, cache reads and writes at their own rates"):
    val ps = Map("m" -> Price(BigDecimal(3), BigDecimal(15), Some(BigDecimal("0.3")), Some(BigDecimal("3.75"))))
    // 1M input of which 200k cache-read and 100k cache-write; 100k output
    val c = Prices.cost(ps, "m", Usage(input = 1000000, output = 100000, cacheRead = 200000, cacheWrite = 100000))
    assertEquals(c, Right(BigDecimal("2.1") + BigDecimal("0.06") + BigDecimal("0.375") + BigDecimal("1.5")))

  test("cost: an UNPRICED model is refused, never free (the inverse of sttp-ai's PriceTable)"):
    val c = Prices.cost(Map.empty, "gpt-x", Usage(10, 10))
    assert(c.left.exists(_.contains("has no price")), c)

  test("billing: a plan model needs no price and costs 0 USD; a metered one is priced"):
    val plan = ModelRef(ProviderKind.ClaudeCode, "sonnet")
    val b = Prices.billing(Map.empty, plan, Some(PlanQuota.PoolId.ClaudePlan))
    assertEquals(b, Right(Billing.Plan(PlanQuota.PoolId.ClaudePlan)))
    assertEquals(b.map(_.cost(Usage(1000000, 1000000))), Right(BigDecimal(0)))
    assertEquals(b.map(_.describe), Right("plan pool: claude-plan"))
    // a price in the table does not make a plan model metered
    assert(Prices.billing(Map("sonnet" -> Price(BigDecimal(3), BigDecimal(15))), plan, Some(PlanQuota.PoolId.ClaudePlan))
      .exists(_.isInstanceOf[Billing.Plan]))
    val m = Prices.billing(Map("m" -> Price(BigDecimal(3), BigDecimal(15))), ModelRef(ProviderKind.Anthropic, "m"), None)
    assertEquals(m.map(_.cost(Usage(1000000, 0))), Right(BigDecimal(3)))
    assertEquals(m.map(_.describe), Right("price: 3/15 USD per million tokens"))

  test("billing: an unpriced METERED model is refused — claude-code on an API key included"):
    val local = Prices.billing(Map.empty, ModelRef(ProviderKind.Local, "qwen"), None)
    assert(local.left.exists(_.contains("has no price")), local)
    val keyed = Prices.billing(Map.empty, ModelRef(ProviderKind.ClaudeCode, "sonnet"), None)
    assert(keyed.left.exists(e => e.contains("has no price") && e.contains("use an API key")), keyed)

  test("price text: two or four numbers, trailing zeros dropped"):
    assertEquals(Price(BigDecimal("3.00"), BigDecimal(15)).text, "3/15")
    assertEquals(Price(BigDecimal(3), BigDecimal(15), Some(BigDecimal("0.3")), Some(BigDecimal("3.75"))).text, "3/15/0.3/3.75")

  test("RunState: legal transitions only; resume is paused → queued"):
    import RunState.*
    assertEquals(next(Queued, Running), Some(Running))
    assertEquals(next(Running, Paused), Some(Paused))
    assertEquals(next(Paused, Queued), Some(Queued))
    assertEquals(next(Paused, Running), None)
    assertEquals(next(Done, Queued), None)
    assertEquals(next(Cancelled, Queued), None)
    assert(Done.isTerminal && Failed.isTerminal && Cancelled.isTerminal && !Paused.isTerminal)

  test("Role and Pipeline parse their wire names"):
    assertEquals(Role.parse("cold-reader"), Some(Role.ColdReader))
    assertEquals(Pipeline.parse("enrich-source"), Some(Pipeline.EnrichSource))
    assertEquals(Pipeline.parse("review-article"), Some(Pipeline.ReviewArticle))
    assertEquals(Pipeline.parse("enrich_source"), None)

  test("each pipeline names its target kind and every role it needs"):
    assertEquals(Pipeline.EnrichSource.targetKind, TargetKind.Source)
    assertEquals(Pipeline.ReviewArticle.targetKind, TargetKind.Article)
    assertEquals(Pipeline.ReviewArticle.roles, List(Role.Reviewer, Role.ColdReader, Role.Skeptic, Role.Fixer))
    assertEquals(TargetKind.parse("article"), Some(TargetKind.Article))

  test("approval vocabularies parse their wire names"):
    assertEquals(ApprovalKind.parse("status-change"), Some(ApprovalKind.StatusChange))
    assertEquals(ApprovalState.parse("open"), Some(ApprovalState.Open))
    assertEquals(Decision.parse(" Approve "), Some(Decision.Approve))
    assertEquals(Decision.parse("accept"), None)

  test("RunParams: missing options decode as their defaults"):
    assertEquals(io.circe.parser.decode[RunParams]("{}"), Right(RunParams()))
    assert(!RunParams().fixes)
    assert(RunParams(fix = Some(true)).fixes)

  test("agents are OFF by default and every key loads; env switches them on"):
    val cfg = AppConfig.load(org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of()))
    assert(!cfg.agents.enabled)
    assertEquals(cfg.agents.maxConcurrentRuns, 4)
    assertEquals(cfg.agents.roles.extractor, "")
    val on = AppConfig.load(org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of(
      "HISTMERSION_AGENTS_ENABLED", "true", "HISTMERSION_AGENTS_ROLE_EXTRACTOR", "local:m", "ANTHROPIC_API_KEY", "sk-secret")))
    assert(on.agents.enabled)
    assertEquals(on.agents.role(Role.Extractor), "local:m")
    assert(!on.agents.providers.anthropic.toString.contains("sk-secret"), "the key must never be printed")
    assert(cfg.agents.autoEnrich, "auto-enrich is on by default (it acts only with agents on)")
    assert(!AppConfig.load(org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of(
      "HISTMERSION_AGENTS_AUTO_ENRICH", "false"))).agents.autoEnrich)

  test("P2 keys: approvals manual, review rounds, trace pruning and prompt overrides by default; env overrides"):
    val cfg = AppConfig.load(org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of()))
    assertEquals(cfg.agents.approvals.statusMode, ApprovalMode.Manual)
    assertEquals(cfg.agents.approvals.edgeMode, ApprovalMode.Manual)
    assertEquals(cfg.agents.review.maxRounds, 3)
    assertEquals(cfg.agents.trace.keepMessagesDays, 30)
    assertEquals(cfg.agents.promptsDir, "")
    assert(cfg.agents.enrich.upstream)
    val on = AppConfig.load(org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of(
      "HISTMERSION_AGENTS_APPROVE_EDGES", "auto", "HISTMERSION_AGENTS_REVIEW_ROUNDS", "2",
      "HISTMERSION_AGENTS_PROMPTS_DIR", "/tmp/p", "HISTMERSION_AGENTS_KEEP_MESSAGES_DAYS", "0")))
    assertEquals(on.agents.approvals.edgeMode, ApprovalMode.Auto)
    assertEquals(on.agents.review.maxRounds, 2)
    assertEquals(on.agents.promptsDir, "/tmp/p")
    assertEquals(on.agents.trace.keepMessagesDays, 0)

  test("RunState: a run waits for an approval only from running, and leaves it only to the queue or cancelled"):
    import RunState.*
    assertEquals(next(Running, WaitingApproval), Some(WaitingApproval))
    assertEquals(next(WaitingApproval, Queued), Some(Queued))
    assertEquals(next(WaitingApproval, Cancelled), Some(Cancelled))
    assertEquals(next(WaitingApproval, Running), None)
    assertEquals(next(WaitingApproval, Paused), None)
    assertEquals(next(Queued, WaitingApproval), None)
    assert(!WaitingApproval.isTerminal)
    assertEquals(RunState.parse("waiting"), Some(WaitingApproval))
    assert(RunInfo("r", "plan-goal", "g", "waiting").isWaiting)

  test("P3 pipelines, targets, roles and blocking approval kinds"):
    assertEquals(Pipeline.parse("plan-goal").map(_.targetKind), Some(TargetKind.Goal))
    assertEquals(Pipeline.CompileArticle.roles, List(Role.Extractor, Role.Writer))
    assertEquals(Pipeline.CurrencyCheck.roles, Nil)
    assertEquals(Pipeline.AcquisitionSweep.roles, List(Role.Planner))
    assert(ApprovalKind.GoalPlan.blocking && ApprovalKind.ArticlePublish.blocking)
    assert(!ApprovalKind.StatusChange.blocking && !ApprovalKind.Edge.blocking)
    assertEquals(GoalPlans.ScheduleKind.parse("acquisition").map(_.pipeline), Some(Pipeline.AcquisitionSweep))
    assertEquals(Origin.parse("scheduler"), Some(Origin.Scheduler))
    // a run stored before P3 reads its new options as absent
    assertEquals(io.circe.parser.decode[RunParams]("""{"fix":true}"""), Right(RunParams(fix = Some(true))))

  test("P3 keys: publishing waits for a person, the scheduler is on but needs agents; env overrides"):
    val cfg = AppConfig.load(org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of()))
    assertEquals(cfg.agents.approvals.publishMode, ApprovalMode.Manual)
    assertEquals(cfg.agents.roles.planner, "")
    assertEquals(cfg.agents.roles.writer, "")
    assert(cfg.agents.scheduler.enabled && cfg.agents.compile.review)
    assertEquals(cfg.agents.plan.caps.works, 15)
    val on = AppConfig.load(org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of(
      "HISTMERSION_AGENTS_APPROVE_PUBLISH", "auto", "HISTMERSION_AGENTS_ROLE_WRITER", "local:w",
      "HISTMERSION_AGENTS_SCHEDULER", "false", "HISTMERSION_AGENTS_COMPILE_MAX_SOURCES", "5")))
    assertEquals(on.agents.approvals.publishMode, ApprovalMode.Auto)
    assertEquals(on.agents.role(Role.Writer), "local:w")
    assert(!on.agents.scheduler.enabled)
    assertEquals(on.agents.compile.maxSources, 5)

  test("Markdown: a plan and a draft as a person reads them before approving; a goal with its links and schedules"):
    import GoalPlans.*
    val plan = Plan("g", "covers it", List(PlannedGoal("g-sub", "Sub")), List("tin isotopes"),
      List(PlannedWork("10.1000/x", Some("X"), Some(2020), citekey = Some("x2020_y"), inCorpus = true, why = "core")),
      List("10.1000/z: not verified upstream"))
    val a = ApprovalInfo(7, Some("run12345"), "goal-plan", "", evidence = "covers it", subject = Some("g"), plan = Some(plan))
    val md = AgentsMarkdown.approvals(List(a))
    assert(md.contains("plan for goal **g**: 1 sub-goal(s), 1 query(ies), 1 work(s)"), md)
    assert(md.contains("in the corpus as [[x2020_y]]: core") && md.contains("dropped: 10.1000/z"), md)
    assertEquals(md.split("covers it", -1).length - 1, 1, "the rationale is shown once")
    val d = ArticleDraft("tin", DraftMode.Extend, "Tin", "concept", "New text [[x2020_y]].", section = Some("## Disagreements"),
      problems = List("a paragraph with no [[citekey]]"))
    val pub = ApprovalInfo(8, None, "article-publish", "", subject = Some("tin"), article = Some(d))
    val md2 = AgentsMarkdown.approvals(List(pub))
    assert(md2.contains("publish a section appended to [[tin]]") && md2.contains("under '## Disagreements'") &&
      md2.contains("- self-check: a paragraph with no [[citekey]]") && md2.contains("New text [[x2020_y]]."), md2)
    val s = GoalSchedule("g", ScheduleKind.Currency, "@daily", enabled = false, Some(0.5), None, None, Some("abcdef123"), Some("skipped: x"))
    assertEquals(AgentsMarkdown.scheduleLine(s), "g · currency · `@daily` · off · cap 0.5000 USD · last run abcdef12 · skipped: x")
    val v = GoalView(Goal("g", "G", "planned"), links = List(GoalLink(LinkKind.Source, "gone2020_x"), GoalLink(LinkKind.Query, "q")),
      schedules = List(s))
    val md3 = AgentsMarkdown.goalView(v)
    assert(md3.contains("- source: [[gone2020_x]] (missing)") && md3.contains("- query: \"q\"") && md3.contains("## Schedules"), md3)

  // ── #992 ───────────────────────────────────────────────────────────────────────────────────────

  test("ModelOverride: role=provider:model, only the first = separates; a missing role or model is an error"):
    assertEquals(ModelOverride.parse(" Skeptic = claude-code:haiku "), Right(ModelOverride("skeptic", "claude-code:haiku")))
    assertEquals(ModelOverride.parse("fixer=local:a > anthropic:b"), Right(ModelOverride("fixer", "local:a > anthropic:b")))
    assert(ModelOverride.parse("skeptic").left.exists(_.contains("<role>=<provider:model>")))
    assert(ModelOverride.parse("=local:x").left.exists(_.contains("names no role")))
    assert(ModelOverride.parse("skeptic=").left.exists(_.contains("names no model")))

  test("a run's model for a role: its --model-for, else its --model, else none (the role's chain)"):
    val r = RunInfo("r", "review-article", "x", "queued", model = Some("anthropic:all"),
      params = RunParams(modelFor = Some(List(ModelOverride("skeptic", "claude-code:haiku")))))
    assertEquals(r.modelFor(Role.Skeptic), Some("claude-code:haiku"))
    assertEquals(r.modelFor(Role.Reviewer), Some("anthropic:all"))
    assertEquals(RunInfo("r", "p", "x", "queued").modelFor(Role.Fixer), None)

  test("a step's model: the configured ref, and the provider's id when it said another"):
    val s = StepInfo(1, "skeptic-1", "llm", 1, "done", model = Some("claude-code:haiku"), actualModel = Some("claude-haiku-5-5"))
    assertEquals(s.modelText, "claude-code:haiku → claude-haiku-5-5")
    assertEquals(s.copy(actualModel = Some("haiku")).modelText, "claude-code:haiku")
    assertEquals(s.copy(actualModel = None).modelText, "claude-code:haiku")
    assertEquals(s.copy(model = None).modelText, "claude-haiku-5-5")
    assertEquals(StepInfo(1, "load", "code", 1, "done").modelText, "")

  test("a dry run is marked in the run line and the run, and each skipped write is shown with its payload"):
    val r = RunInfo("r1", "enrich-source", "x2020", "done", dryRun = true,
      params = RunParams(modelFor = Some(List(ModelOverride("extractor", "local:h")))))
    assert(AgentsMarkdown.runLine(r).contains("(dry run, writes nothing, extractor=local:h)"), AgentsMarkdown.runLine(r))
    val d = RunDetail(r, List(StepInfo(8, "patch", "code", 1, "done", wouldWrite = List(WouldWrite("patch [[x2020]]: summary", """{"summary":"S"}""")))), Nil)
    val md = AgentsMarkdown.run(d, withMessages = false)
    assert(md.startsWith("# Agent run r1 (dry run)"), md)
    assert(md.contains("| 8 | patch | code | 1 | done |  | 0/0 | 0.0000 | dry run: not written: patch [[x2020]]: summary |"), md)
    assert(md.contains("### patch: patch [[x2020]]: summary — dry run: not written\n\n```json\n{\"summary\":\"S\"}\n```"), md)
    assert(!AgentsMarkdown.run(d.copy(run = r.copy(dryRun = false), steps = Nil), withMessages = false).contains("dry run"))
