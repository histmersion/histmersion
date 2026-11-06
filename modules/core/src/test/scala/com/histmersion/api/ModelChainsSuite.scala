package com.histmersion.api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import com.histmersion.api.automation.Agents.{ModelRef, ProviderKind}
import com.histmersion.api.automation.ModelChains.*
import com.histmersion.api.automation.{Agents, AgentsMarkdown}

class ModelChainsSuite extends munit.FunSuite:

  private val Sonnet = ModelRef(ProviderKind.ClaudeCode, com.histmersion.api.automation.Agents.ModelRef.Model("sonnet"))
  private val Qwen = ModelRef(ProviderKind.Local, com.histmersion.api.automation.Agents.ModelRef.Model("qwen2.5-coder:32b"))

  test("a chain is models separated by >, tried in order; one model is a chain of one; blank is none"):
    assertEquals(parseChain(com.histmersion.api.automation.ModelChains.Chain.Spec("claude-code:sonnet > local:qwen2.5-coder:32b")), Right(List(Sonnet, Qwen)))
    assertEquals(parseChain(com.histmersion.api.automation.ModelChains.Chain.Spec("claude-code:sonnet")), Right(List(Sonnet)))
    assertEquals(parseChain(com.histmersion.api.automation.ModelChains.Chain.Spec("  ")), Right(Nil))
    assertEquals(chainText(List(Sonnet, Qwen)), "claude-code:sonnet > local:qwen2.5-coder:32b")

  test("a chain reports every bad model and refuses a model named twice"):
    val e = parseChain(com.histmersion.api.automation.ModelChains.Chain.Spec("ollama:x > local: > claude-code:sonnet")).left.toOption.get
    assert(e.contains("unknown provider 'ollama'") && e.contains("names no model"), e)
    assert(parseModels(List(com.histmersion.api.automation.Agents.ModelRef.Model.Spec("claude-code:sonnet"), com.histmersion.api.automation.Agents.ModelRef.Model.Spec("claude-code:sonnet"))).left.exists(_.contains("twice")))

  test("claude-code is a provider; its model ids are whatever the CLI accepts"):
    assertEquals(
      ModelRef.parse(com.histmersion.api.automation.Agents.ModelRef.Wire("claude-code:claude-sonnet-4-5")),
      Right(ModelRef(ProviderKind.ClaudeCode, com.histmersion.api.automation.Agents.ModelRef.Model("claude-sonnet-4-5")))
    )

  test("windows: names, n m|h|d|w, bounded to 1 minute .. 90 days, written back in the shortest exact unit"):
    assertEquals(Window.parse(com.histmersion.api.automation.ModelChains.LimitSpec.Window("5h")).map(_.seconds), Right(5 * 3600L))
    assertEquals(Window.parse(com.histmersion.api.automation.ModelChains.LimitSpec.Window("week")).map(_.text), Right("7d"))
    assertEquals(Window.parse(com.histmersion.api.automation.ModelChains.LimitSpec.Window("2w")).map(_.text), Right("14d"))
    assertEquals(Window.parse(com.histmersion.api.automation.ModelChains.LimitSpec.Window("month")).map(_.text), Right("30d"))
    assertEquals(Window.parse(com.histmersion.api.automation.ModelChains.LimitSpec.Window("90m")).map(_.text), Right("90m"))
    assert(Window.parse(com.histmersion.api.automation.ModelChains.LimitSpec.Window("0h")).isLeft)
    assert(Window.parse(com.histmersion.api.automation.ModelChains.LimitSpec.Window("91d")).isLeft)
    assert(Window.parse(com.histmersion.api.automation.ModelChains.LimitSpec.Window("fortnight")).left.exists(_.contains("not a window")))

  test("limits: tokens with k/M suffixes, usd with a word or a dollar sign"):
    assertEquals(Limit.parse(com.histmersion.api.automation.ModelChains.Limit.Spec("500k tokens/5h")), Right(Limit(BigDecimal(500000), Measure.Tokens, Window(com.histmersion.api.automation.ModelChains.Window.Seconds(18000)))))
    assertEquals(Limit.parse(com.histmersion.api.automation.ModelChains.Limit.Spec("2M tokens/week")).map(_.text), Right("2M tokens/7d"))
    assertEquals(Limit.parse(com.histmersion.api.automation.ModelChains.Limit.Spec("$5/day")), Right(Limit(BigDecimal(5), Measure.Usd, Window(com.histmersion.api.automation.ModelChains.Window.Seconds(86400)))))
    assertEquals(Limit.parse(com.histmersion.api.automation.ModelChains.Limit.Spec("2.5 usd/day")).map(_.text), Right("2.5 usd/1d"))
    assertEquals(Limit.parse(com.histmersion.api.automation.ModelChains.Limit.Spec("1.5M tokens/5h")).map(_.amount), Right(BigDecimal(1500000)))

  test("limits: a fractional token count, a bad unit, no window, a non-positive amount are errors"):
    assert(Limit.parse(com.histmersion.api.automation.ModelChains.Limit.Spec("1.5 tokens/5h")).left.exists(_.contains("whole number")))
    assert(Limit.parse(com.histmersion.api.automation.ModelChains.Limit.Spec("5 euros/day")).left.exists(_.contains("neither tokens nor usd")))
    assert(Limit.parse(com.histmersion.api.automation.ModelChains.Limit.Spec("5 usd")).isLeft)
    assert(Limit.parse(com.histmersion.api.automation.ModelChains.Limit.Spec("0 usd/day")).left.exists(_.contains("positive")))
    assert(Limit.fromSpec(LimitSpec(com.histmersion.api.automation.ModelChains.LimitSpec.Amount(-1), Measure.Usd, com.histmersion.api.automation.ModelChains.LimitSpec.Window("day"))).isLeft)
    assertEquals(Limit.fromSpec(LimitSpec(com.histmersion.api.automation.ModelChains.LimitSpec.Amount(500000), Measure.Tokens, com.histmersion.api.automation.ModelChains.LimitSpec.Window("5h"))).map(_.text), Right("500k tokens/5h"))

  test("allowances: the config grammar, provider-wide targets, a reserve"):
    val as = parseAllowances(
      "claude-code:* = 2M tokens/5h, 20M tokens/7d, reserve 20%; local:qwen2.5-coder:32b = 1 usd/day"
    ).toOption.get
    assertEquals(as.map(_.target.wire), List("claude-code:*", "local:qwen2.5-coder:32b"))
    assertEquals(as.head.reservePct, 20)
    assertEquals(as.head.limits.map(_.text), List("2M tokens/5h", "20M tokens/7d"))
    assert(as.head.target.covers(Sonnet) && !as.head.target.covers(Qwen))
    assert(as(1).target.covers(Qwen))
    assertEquals(as.head.text, "2M tokens/5h, 20M tokens/7d, reserve 20%")
    assertEquals(parseAllowances(com.histmersion.api.automation.ModelChains.Allowance.Spec("")), Right(Nil))

  test("allowances: every malformed entry is reported — a typo must not lift a cap"):
    val e = parseAllowances(
      "claude-code:* 2M tokens/5h; nobody:* = 1 usd/day; local:x = 3 bananas/day; local:y = 1 usd/day, reserve 120%"
    ).left.toOption.get
    List("is not <provider:model", "unknown provider 'nobody'", "neither tokens nor usd", "0 to 99").foreach(m =>
      assert(e.contains(m), e)
    )
    assert(parseAllowances(com.histmersion.api.automation.ModelChains.Allowance.Spec("local:x = 1 usd/day; local:x = 2 usd/day")).left.exists(_.contains("twice")))
    assert(parseAllowances(com.histmersion.api.automation.ModelChains.Allowance.Spec("local:x = 1 usd/day, 2 usd/day")).left.exists(_.contains("same measure and window")))

  test("chains and allowances name a plugin provider only while it is registered"):
    assert(parseChain(com.histmersion.api.automation.ModelChains.Chain.Spec("gemini:pro > local:x")).left.exists(_.contains("unknown provider 'gemini'")))
    assertEquals(
      parseChain(com.histmersion.api.automation.ModelChains.Chain.Spec("gemini:pro > local:x"), Set(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini"))).map(_.map(_.wire)),
      Right(List("gemini:pro", "local:x"))
    )
    assert(parseAllowances(com.histmersion.api.automation.ModelChains.Allowance.Spec("gemini:* = 1 usd/day")).left.exists(_.contains("unknown provider 'gemini'")))
    val a = parseAllowances(com.histmersion.api.automation.ModelChains.Allowance.Spec("gemini:* = 1 usd/day"), Set(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini"))).toOption.get.head
    assert(a.target.covers(ModelRef(ProviderKind.Plugin(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini")), com.histmersion.api.automation.Agents.ModelRef.Model("pro"))))
    assert(!a.target.covers(ModelRef(ProviderKind.Local, com.histmersion.api.automation.Agents.ModelRef.Model("pro"))))
    assertEquals(Target.parse(com.histmersion.api.automation.ModelChains.Target.Spec("gemini:pro"), Set(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini"))).map(_.wire), Right("gemini:pro"))

  private val H = 3600L * 1000L

  test("a limit counts only its rolling window; usable while used < amount − reserve"):
    val l = Limit(BigDecimal(1000), Measure.Tokens, Window(com.histmersion.api.automation.ModelChains.Window.Seconds(3600)))
    val now = 10 * H
    val spends = List(Spend(now - 2 * H, com.histmersion.api.automation.ModelChains.Spend.Tokens(900), 0), Spend(now - H / 2, com.histmersion.api.automation.ModelChains.Spend.Tokens(600), 0), Spend(now - H / 4, com.histmersion.api.automation.ModelChains.Spend.Tokens(100), 0))
    val s = limitState(l, com.histmersion.api.automation.ModelChains.Allowance.ReservePct(0), spends, now)
    assertEquals(s.used, BigDecimal(700)) // the one two hours ago has left the window
    assertEquals(s.remaining, BigDecimal(300))
    assert(s.usable)
    // a 40% reserve: 400 must stay, so 700 used is over the line
    val r = limitState(l, com.histmersion.api.automation.ModelChains.Allowance.ReservePct(40), spends, now)
    assertEquals(r.reserve, BigDecimal(400))
    assert(!r.usable)

  test("freesAt: when enough of the window's oldest spend has aged out"):
    val l = Limit(BigDecimal(1000), Measure.Tokens, Window(com.histmersion.api.automation.ModelChains.Window.Seconds(3600)))
    val now = 10 * H
    val spends = List(Spend(now - 50 * 60000L, com.histmersion.api.automation.ModelChains.Spend.Tokens(600), 0), Spend(now - 10 * 60000L, com.histmersion.api.automation.ModelChains.Spend.Tokens(500), 0))
    val s = limitState(l, com.histmersion.api.automation.ModelChains.Allowance.ReservePct(0), spends, now)
    assert(!s.usable)
    // the 600 leaves the window 10 minutes from now: 500 < 1000 then
    assertEquals(s.freesAt, Some(now + 10 * 60000L))
    // with a 60% reserve (threshold 400) the 500 must leave too: 50 minutes from now
    assertEquals(limitState(l, com.histmersion.api.automation.ModelChains.Allowance.ReservePct(60), spends, now).freesAt, Some(now + 50 * 60000L))
    assertEquals(s.headroom, BigDecimal(0))

  test("usd limits sum cost; verdict needs every limit, frees at the latest; headroom is the least left"):
    val day = Limit(BigDecimal(5), Measure.Usd, Window(com.histmersion.api.automation.ModelChains.Window.Seconds(86400)))
    val hour = Limit(BigDecimal(80000), Measure.Tokens, Window(com.histmersion.api.automation.ModelChains.Window.Seconds(3600)))
    val now = 100 * H
    val spends = List(Spend(now - 20 * H, com.histmersion.api.automation.ModelChains.Spend.Tokens(10), BigDecimal("4.5")), Spend(now - 30 * 60000L, com.histmersion.api.automation.ModelChains.Spend.Tokens(90000), BigDecimal("0.6")))
    val ss = List(limitState(day, com.histmersion.api.automation.ModelChains.Allowance.ReservePct(0), spends, now), limitState(hour, com.histmersion.api.automation.ModelChains.Allowance.ReservePct(0), spends, now))
    assert(!ss.head.usable && !ss(1).usable)
    val (ok, frees) = verdict(ss)
    assert(!ok)
    assertEquals(frees, Some(now - 20 * H + 86400L * 1000L), "the day limit frees last")
    val fine = List(
      limitState(day, com.histmersion.api.automation.ModelChains.Allowance.ReservePct(0), Nil, now),
      limitState(hour.modify(_.amount).setTo(BigDecimal(100000)), com.histmersion.api.automation.ModelChains.Allowance.ReservePct(10), List(Spend(now - 1000, com.histmersion.api.automation.ModelChains.Spend.Tokens(50000), 0)), now)
    )
    assertEquals(verdict(fine), (true, None))
    assertEquals(headroom(fine), (Some(40000L), Some(BigDecimal(5))))

  test("a reserve as large as the limit never frees by itself"):
    val l = Limit(BigDecimal(10), Measure.Usd, Window(com.histmersion.api.automation.ModelChains.Window.Seconds(3600)))
    val s = limitState(l, com.histmersion.api.automation.ModelChains.Allowance.ReservePct(99), List(Spend(com.histmersion.api.automation.ModelChains.Spend.At(1000), com.histmersion.api.automation.ModelChains.Spend.Tokens(0), BigDecimal("0.2"))), com.histmersion.api.automation.ModelChains.LimitState.Now(2000))
    assert(!s.usable)
    assertEquals(s.freesAt, Some(1000 + 3600L * 1000L)) // 0 < 0.1 once the spend leaves
    assertEquals(verdict(List(s.modify(_.freesAt).setTo((None: _root_.scala.Option[_root_.scala.Long])))), (false, None))

  test("Markdown: the chains with the model a call would use, the allowances with use and reset, the doctor"):
    import com.histmersion.api.automation.Agents.*
    val m = ModelsInfo(
      enabled = com.histmersion.api.automation.Agents.ModelsInfo.Enabled(true),
      Nil,
      List(com.histmersion.api.automation.Agents.ModelsInfo.Pipelines("enrich-source")),
      com.histmersion.api.automation.Agents.ModelsInfo.MaxConcurrentRuns(4),
      Nil,
      chains = List(
        RoleChain(
          com.histmersion.api.automation.ModelChains.RoleChain.Role("extractor"),
          com.histmersion.api.automation.ModelChains.RoleChain.Source("saved"),
          List(
            ChainEntry(
              com.histmersion.api.automation.ModelChains.ChainEntry.Model("claude-code:sonnet"),
              ready = com.histmersion.api.automation.ModelChains.ChainEntry.Ready(true),
              usable = com.histmersion.api.automation.ModelChains.ChainEntry.Usable(false),
              Some(com.histmersion.api.automation.ModelChains.ChainEntry.Problem("below its allowance")),
              Some(com.histmersion.api.automation.ModelChains.LimitStatus.FreesAt("2026-10-05T14:00:00Z"))
            ),
            ChainEntry(com.histmersion.api.automation.ModelChains.ChainEntry.Model("local:qwen"), ready = com.histmersion.api.automation.ModelChains.ChainEntry.Ready(true), usable = com.histmersion.api.automation.ModelChains.ChainEntry.Usable(true))
          ),
          Some(com.histmersion.api.automation.ModelChains.RoleChain.Active("local:qwen"))
        )
      ),
      allowances = List(
        AllowanceStatus(
          com.histmersion.api.automation.ModelChains.AllowanceStatus.Target("claude-code:*"),
          com.histmersion.api.automation.ModelChains.AllowanceStatus.Source("config"),
          com.histmersion.api.automation.ModelChains.AllowanceStatus.ReservePct(20),
          List(
            LimitStatus(
              com.histmersion.api.automation.ModelChains.LimitStatus.Limit("2M tokens/5h"),
              Measure.Tokens,
              com.histmersion.api.automation.ModelChains.LimitStatus.Window("5h"),
              com.histmersion.api.automation.ModelChains.LimitStatus.Amount(2000000),
              com.histmersion.api.automation.ModelChains.LimitStatus.Used(1900000),
              com.histmersion.api.automation.ModelChains.LimitStatus.Remaining(100000),
              com.histmersion.api.automation.ModelChains.LimitStatus.Reserve(400000),
              usable = com.histmersion.api.automation.ModelChains.LimitStatus.Usable(false),
              Some(com.histmersion.api.automation.ModelChains.LimitStatus.FreesAt("2026-10-05T14:00:00Z"))
            )
          ),
          usable = com.histmersion.api.automation.ModelChains.AllowanceStatus.Usable(false),
          Some(com.histmersion.api.automation.ModelChains.LimitStatus.FreesAt("2026-10-05T14:00:00Z")),
          Some(com.histmersion.api.automation.ModelChains.AllowanceStatus.Note("counts what claude -p reported"))
        )
      )
    )
    val md = AgentsMarkdown.models(m)
    assert(
      md.contains(
        "| extractor | `claude-code:sonnet` (paused until 2026-10-05T14:00:00Z) → `local:qwen` | local:qwen | saved |"
      ),
      md
    )
    assert(md.contains("`claude-code:*` (config, reserve 20%) — **below its reserve** until 2026-10-05T14:00:00Z"), md)
    assert(md.contains("2M tokens/5h: 1900k used, 100k left, reserve 400k — frees 2026-10-05T14:00:00Z"), md)
    val none = AgentsMarkdown.models(
      m.modify(_.chains).setTo(List(RoleChain(com.histmersion.api.automation.ModelChains.RoleChain.Role("writer"), com.histmersion.api.automation.ModelChains.RoleChain.Source("config"), Nil, None, Some(com.histmersion.api.automation.ModelChains.RoleChain.Problem("no model"))))).modify(_.allowances).setTo(Nil)
    )
    assert(none.contains("| writer | — | no model | config |") && none.contains("No allowance"), none)
    val d = DoctorReport(
      enabled = com.histmersion.api.automation.Agents.DoctorReport.Enabled(false),
      online = com.histmersion.api.automation.Agents.DoctorReport.Online(false),
      List(
        ProviderDoctor(
          com.histmersion.api.automation.Agents.ProviderDoctor.Provider("claude-code"),
          used = com.histmersion.api.automation.Agents.ProviderDoctor.Used(true),
          List(com.histmersion.api.automation.Agents.ProviderDoctor.Models("claude-code:sonnet")),
          List(
            DoctorCheck(com.histmersion.api.automation.Agents.DoctorCheck.Name("login"), CheckLevel.Ok, com.histmersion.api.automation.Agents.DoctorCheck.Detail("logged in (claude.ai, plan max)")),
            DoctorCheck(com.histmersion.api.automation.Agents.DoctorCheck.Name("bind"), CheckLevel.Fail, com.histmersion.api.automation.Agents.DoctorCheck.Detail("refused"))
          )
        )
      ),
      List(DoctorCheck(com.histmersion.api.automation.Agents.DoctorCheck.Name("agents"), CheckLevel.Warn, com.histmersion.api.automation.Agents.DoctorCheck.Detail("off")))
    )
    val dm = AgentsMarkdown.doctor(d)
    assert(
      dm.contains("# Agent doctor (offline)") && dm.contains(
        "## claude-code — **FAIL** (used by: claude-code:sonnet)"
      ) &&
        dm.contains("- **warn** · agents: off"),
      dm
    )
