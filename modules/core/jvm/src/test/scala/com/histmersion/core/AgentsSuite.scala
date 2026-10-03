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
