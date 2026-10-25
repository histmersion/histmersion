package com.histmersion.server.agents

import com.histmersion.api.*
import com.histmersion.api.Agents.*
import com.histmersion.api.ModelChains.*
import com.histmersion.config.*
import com.histmersion.domain.ReadSchedule

import java.time.Duration

/** The agents' settings as the server reads them from config: approval modes, roles, providers and
  * their defaults, the enrichment guards, a plan pool's read schedule. */
class AgentsSettingsSuite extends munit.FunSuite:

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

  test("config: claude-code is guarded by default (no API key, loopback only); chains and allowances load from env"):
    val cfg = AppConfig.load(org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of()))
    val cc  = cfg.agents.providers.claudeCode
    assertEquals(cc.command, "claude")
    assert(!cc.useApiKey && !cc.allowNonLoopback)
    assertEquals(cc.extraArgs, "", "the settings files stay out through --setting-sources, which keeps the plan login (§3a P2)")
    assert(cfg.agents.runtimes.claudeMod && cfg.agents.runtimes.generate && !cfg.agents.runtimes.interactiveMod)
    assertEquals(cfg.agents.quota.claudePlan.maxCallsInFlight, 1)
    assertEquals((cfg.agents.quota.claudePlan.refresh, cfg.agents.quota.claudePlan.probeModel), ("auto", "haiku"),
      "a stale or missing Claude reading is refreshed by the probe (#989)")
    assertEquals(cfg.agents.quota.fallthrough, "", "no chain falls from a plan to a paid key unless asked")
    assert(!cfg.agents.providers.codex.useApiKey && !cfg.agents.providers.opencode.chatgptLogin)
    assertEquals(cfg.agents.provider(ProviderKind.Codex), None)
    assertEquals(cfg.agents.allowances, "")
    assertEquals(cfg.agents.provider(ProviderKind.ClaudeCode), None, "claude-code is no HTTP provider")
    val on = AppConfig.load(org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of(
      "HISTMERSION_AGENTS_ROLE_EXTRACTOR", "claude-code:sonnet > local:qwen",
      "HISTMERSION_AGENTS_ALLOWANCES", "claude-code:* = 2M tokens/5h, reserve 20%",
      "HISTMERSION_AGENTS_CLAUDE_COMMAND", "/opt/homebrew/bin/claude",
      "HISTMERSION_AGENTS_CLAUDE_ALLOW_NON_LOOPBACK", "true")))
    assertEquals(parseChain(on.agents.role(Agents.Role.Extractor)).map(_.map(_.wire)), Right(List("claude-code:sonnet", "local:qwen")))
    assertEquals(parseAllowances(on.agents.allowances).map(_.map(_.text)), Right(List("2M tokens/5h, reserve 20%")))
    assertEquals(on.agents.providers.claudeCode.command, "/opt/homebrew/bin/claude")
    assert(on.agents.providers.claudeCode.allowNonLoopback)

  test("config: article-plan/goal-plan take auto or checked; article-publish only checked reaches the supervisor"):
    val c = AgentApprovalsConfig("manual", "manual", "auto", articlePlan = "Auto", goalPlan = "checked")
    assert(c.checked(ApprovalKind.ArticlePlan) && c.checked(ApprovalKind.GoalPlan))
    assert(c.checked(ApprovalKind.ArticlePublish), "auto publishes in the run; what it left open the supervisor checks")
    assert(!AgentApprovalsConfig("manual", "manual", "manual").checked(ApprovalKind.ArticlePlan))
    assert(!c.checked(ApprovalKind.Edge))
    assertEquals(c.publishMode, Agents.ApprovalMode.Auto)
    assertEquals(c.copy(articlePublish = "checked").publishMode, Agents.ApprovalMode.Manual, "checked: the run waits, the supervisor decides")

  test("config: the guards' defaults and env overrides"):
    val cfg = AppConfig.load(org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of())).agents.enrich.guards
    assertEquals(cfg.minPlacedShare, 0.5)
    assertEquals(cfg.centuryGapYears, 500)
    assertEquals(cfg.phrases, List("this date", "around this date", "the next column", "the previous column",
      "the table above", "the table below", "as listed"))
    val on = AppConfig.load(org.ekrich.config.ConfigFactory.parseMap(java.util.Map.of(
      "HISTMERSION_AGENTS_ENRICH_MIN_PLACED_SHARE", "0.75", "HISTMERSION_AGENTS_ENRICH_CENTURY_GAP_YEARS", "300",
      "HISTMERSION_AGENTS_ENRICH_POINTER_PHRASES", " See Table 2 ,, ibid "))).agents.enrich.guards
    assertEquals((on.minPlacedShare, on.centuryGapYears, on.phrases), (0.75, 300, List("see table 2", "ibid")))
    val g = on.guards((_, _, _) => true)
    assertEquals((g.minPlacedShare, g.centuryGapYears, g.pointerPhrases), (0.75, 300L, List("see table 2", "ibid")))

  test("a plan pool's read schedule: 0 is off, minutes become the interval"):
    assertEquals(ScheduledRefresh.Off.readSchedule, ReadSchedule.Off)
    assertEquals(ScheduledRefresh.fromMinutes(180).map(_.readSchedule), Right(ReadSchedule.Every(Duration.ofMinutes(180))))
