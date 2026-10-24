package com.histmersion.api

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import com.histmersion.api.Agents.{ModelRef, ProviderKind}
import com.histmersion.api.ModelChains.*

class ModelChainsSuite extends munit.FunSuite:

  private val Sonnet = ModelRef(ProviderKind.ClaudeCode, "sonnet")
  private val Qwen   = ModelRef(ProviderKind.Local, "qwen2.5-coder:32b")

  test("a chain is models separated by >, tried in order; one model is a chain of one; blank is none"):
    assertEquals(parseChain("claude-code:sonnet > local:qwen2.5-coder:32b"), Right(List(Sonnet, Qwen)))
    assertEquals(parseChain("claude-code:sonnet"), Right(List(Sonnet)))
    assertEquals(parseChain("  "), Right(Nil))
    assertEquals(chainText(List(Sonnet, Qwen)), "claude-code:sonnet > local:qwen2.5-coder:32b")

  test("a chain reports every bad model and refuses a model named twice"):
    val e = parseChain("ollama:x > local: > claude-code:sonnet").left.toOption.get
    assert(e.contains("unknown provider 'ollama'") && e.contains("names no model"), e)
    assert(parseModels(List("claude-code:sonnet", "claude-code:sonnet")).left.exists(_.contains("twice")))

  test("claude-code is a provider; its model ids are whatever the CLI accepts"):
    assertEquals(ModelRef.parse("claude-code:claude-sonnet-4-5"), Right(ModelRef(ProviderKind.ClaudeCode, "claude-sonnet-4-5")))

  test("windows: names, n m|h|d|w, bounded to 1 minute .. 90 days, written back in the shortest exact unit"):
    assertEquals(Window.parse("5h").map(_.seconds), Right(5 * 3600L))
    assertEquals(Window.parse("week").map(_.text), Right("7d"))
    assertEquals(Window.parse("2w").map(_.text), Right("14d"))
    assertEquals(Window.parse("month").map(_.text), Right("30d"))
    assertEquals(Window.parse("90m").map(_.text), Right("90m"))
    assert(Window.parse("0h").isLeft)
    assert(Window.parse("91d").isLeft)
    assert(Window.parse("fortnight").left.exists(_.contains("not a window")))

  test("limits: tokens with k/M suffixes, usd with a word or a dollar sign"):
    assertEquals(Limit.parse("500k tokens/5h"), Right(Limit(BigDecimal(500000), Measure.Tokens, Window(18000))))
    assertEquals(Limit.parse("2M tokens/week").map(_.text), Right("2M tokens/7d"))
    assertEquals(Limit.parse("$5/day"), Right(Limit(BigDecimal(5), Measure.Usd, Window(86400))))
    assertEquals(Limit.parse("2.5 usd/day").map(_.text), Right("2.5 usd/1d"))
    assertEquals(Limit.parse("1.5M tokens/5h").map(_.amount), Right(BigDecimal(1500000)))

  test("limits: a fractional token count, a bad unit, no window, a non-positive amount are errors"):
    assert(Limit.parse("1.5 tokens/5h").left.exists(_.contains("whole number")))
    assert(Limit.parse("5 euros/day").left.exists(_.contains("neither tokens nor usd")))
    assert(Limit.parse("5 usd").isLeft)
    assert(Limit.parse("0 usd/day").left.exists(_.contains("positive")))
    assert(Limit.fromSpec(LimitSpec(-1, Measure.Usd, "day")).isLeft)
    assertEquals(Limit.fromSpec(LimitSpec(500000, Measure.Tokens, "5h")).map(_.text), Right("500k tokens/5h"))

  test("allowances: the config grammar, provider-wide targets, a reserve"):
    val as = parseAllowances(
      "claude-code:* = 2M tokens/5h, 20M tokens/7d, reserve 20%; local:qwen2.5-coder:32b = 1 usd/day").toOption.get
    assertEquals(as.map(_.target.wire), List("claude-code:*", "local:qwen2.5-coder:32b"))
    assertEquals(as.head.reservePct, 20)
    assertEquals(as.head.limits.map(_.text), List("2M tokens/5h", "20M tokens/7d"))
    assert(as.head.target.covers(Sonnet) && !as.head.target.covers(Qwen))
    assert(as(1).target.covers(Qwen))
    assertEquals(as.head.text, "2M tokens/5h, 20M tokens/7d, reserve 20%")
    assertEquals(parseAllowances(""), Right(Nil))

  test("allowances: every malformed entry is reported — a typo must not lift a cap"):
    val e = parseAllowances("claude-code:* 2M tokens/5h; nobody:* = 1 usd/day; local:x = 3 bananas/day; local:y = 1 usd/day, reserve 120%")
      .left.toOption.get
    List("is not <provider:model", "unknown provider 'nobody'", "neither tokens nor usd", "0 to 99").foreach(m => assert(e.contains(m), e))
    assert(parseAllowances("local:x = 1 usd/day; local:x = 2 usd/day").left.exists(_.contains("twice")))
    assert(parseAllowances("local:x = 1 usd/day, 2 usd/day").left.exists(_.contains("same measure and window")))

  test("chains and allowances name a plugin provider only while it is registered"):
    assert(parseChain("gemini:pro > local:x").left.exists(_.contains("unknown provider 'gemini'")))
    assertEquals(parseChain("gemini:pro > local:x", Set("gemini")).map(_.map(_.wire)), Right(List("gemini:pro", "local:x")))
    assert(parseAllowances("gemini:* = 1 usd/day").left.exists(_.contains("unknown provider 'gemini'")))
    val a = parseAllowances("gemini:* = 1 usd/day", Set("gemini")).toOption.get.head
    assert(a.target.covers(ModelRef(ProviderKind.Plugin("gemini"), "pro")))
    assert(!a.target.covers(ModelRef(ProviderKind.Local, "pro")))
    assertEquals(Target.parse("gemini:pro", Set("gemini")).map(_.wire), Right("gemini:pro"))

  private val H = 3600L * 1000L

  test("a limit counts only its rolling window; usable while used < amount − reserve"):
    val l = Limit(BigDecimal(1000), Measure.Tokens, Window(3600))
    val now = 10 * H
    val spends = List(Spend(now - 2 * H, 900, 0), Spend(now - H / 2, 600, 0), Spend(now - H / 4, 100, 0))
    val s = limitState(l, 0, spends, now)
    assertEquals(s.used, BigDecimal(700))   // the one two hours ago has left the window
    assertEquals(s.remaining, BigDecimal(300))
    assert(s.usable)
    // a 40% reserve: 400 must stay, so 700 used is over the line
    val r = limitState(l, 40, spends, now)
    assertEquals(r.reserve, BigDecimal(400))
    assert(!r.usable)

  test("freesAt: when enough of the window's oldest spend has aged out"):
    val l = Limit(BigDecimal(1000), Measure.Tokens, Window(3600))
    val now = 10 * H
    val spends = List(Spend(now - 50 * 60000L, 600, 0), Spend(now - 10 * 60000L, 500, 0))
    val s = limitState(l, 0, spends, now)
    assert(!s.usable)
    // the 600 leaves the window 10 minutes from now: 500 < 1000 then
    assertEquals(s.freesAt, Some(now + 10 * 60000L))
    // with a 60% reserve (threshold 400) the 500 must leave too: 50 minutes from now
    assertEquals(limitState(l, 60, spends, now).freesAt, Some(now + 50 * 60000L))
    assertEquals(s.headroom, BigDecimal(0))

  test("usd limits sum cost; verdict needs every limit, frees at the latest; headroom is the least left"):
    val day  = Limit(BigDecimal(5), Measure.Usd, Window(86400))
    val hour = Limit(BigDecimal(80000), Measure.Tokens, Window(3600))
    val now = 100 * H
    val spends = List(Spend(now - 20 * H, 10, BigDecimal("4.5")), Spend(now - 30 * 60000L, 90000, BigDecimal("0.6")))
    val ss = List(limitState(day, 0, spends, now), limitState(hour, 0, spends, now))
    assert(!ss.head.usable && !ss(1).usable)
    val (ok, frees) = verdict(ss)
    assert(!ok)
    assertEquals(frees, Some(now - 20 * H + 86400L * 1000L), "the day limit frees last")
    val fine = List(limitState(day, 0, Nil, now), limitState(hour.copy(amount = BigDecimal(100000)), 10,
      List(Spend(now - 1000, 50000, 0)), now))
    assertEquals(verdict(fine), (true, None))
    assertEquals(headroom(fine), (Some(40000L), Some(BigDecimal(5))))

  test("a reserve as large as the limit never frees by itself"):
    val l = Limit(BigDecimal(10), Measure.Usd, Window(3600))
    val s = limitState(l, 99, List(Spend(1000, 0, BigDecimal("0.2"))), 2000)
    assert(!s.usable)
    assertEquals(s.freesAt, Some(1000 + 3600L * 1000L)) // 0 < 0.1 once the spend leaves
    assertEquals(verdict(List(s.copy(freesAt = None))), (false, None))

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

  test("Markdown: the chains with the model a call would use, the allowances with use and reset, the doctor"):
    import Agents.*
    val m = ModelsInfo(enabled = true, Nil, List("enrich-source"), 4, Nil,
      chains = List(RoleChain("extractor", "saved", List(
        ChainEntry("claude-code:sonnet", ready = true, usable = false, Some("below its allowance"), Some("2026-10-05T14:00:00Z")),
        ChainEntry("local:qwen", ready = true, usable = true)), Some("local:qwen"))),
      allowances = List(AllowanceStatus("claude-code:*", "config", 20, List(
        LimitStatus("2M tokens/5h", Measure.Tokens, "5h", 2000000, 1900000, 100000, 400000, usable = false, Some("2026-10-05T14:00:00Z"))),
        usable = false, Some("2026-10-05T14:00:00Z"), Some("counts what claude -p reported"))))
    val md = AgentsMarkdown.models(m)
    assert(md.contains("| extractor | `claude-code:sonnet` (paused until 2026-10-05T14:00:00Z) → `local:qwen` | local:qwen | saved |"), md)
    assert(md.contains("`claude-code:*` (config, reserve 20%) — **below its reserve** until 2026-10-05T14:00:00Z"), md)
    assert(md.contains("2M tokens/5h: 1900k used, 100k left, reserve 400k — frees 2026-10-05T14:00:00Z"), md)
    val none = AgentsMarkdown.models(m.copy(chains = List(RoleChain("writer", "config", Nil, None, Some("no model"))), allowances = Nil))
    assert(none.contains("| writer | — | no model | config |") && none.contains("No allowance"), none)
    val d = DoctorReport(enabled = false, online = false, List(ProviderDoctor("claude-code", used = true, List("claude-code:sonnet"),
      List(DoctorCheck("login", CheckLevel.Ok, "logged in (claude.ai, plan max)"), DoctorCheck("bind", CheckLevel.Fail, "refused")))),
      List(DoctorCheck("agents", CheckLevel.Warn, "off")))
    val dm = AgentsMarkdown.doctor(d)
    assert(dm.contains("# Agent doctor (offline)") && dm.contains("## claude-code — **FAIL** (used by: claude-code:sonnet)") &&
      dm.contains("- **warn** · agents: off"), dm)
