package com.histmersion
package api

import com.histmersion.api.automation.modelchains.Measure
import com.histmersion.api.automation.agents.ModelRef
import com.histmersion.api.automation.modelchains.LimitSpec
import com.histmersion.api.automation.modelchains.Allowance
import com.histmersion.api.automation.modelchains.Spend
import com.histmersion.api.automation.modelchains.LimitState
import com.histmersion.api.automation.agents.ModelsInfo
import com.histmersion.api.automation.modelchains.RoleChain
import com.histmersion.api.automation.modelchains.SettingSource
import com.histmersion.api.automation.modelchains.ChainEntry
import com.histmersion.api.automation.modelchains.LimitStatus
import com.histmersion.api.automation.modelchains.AllowanceStatus
import com.histmersion.api.automation.agents.DoctorReport
import com.histmersion.api.automation.agents.ProviderDoctor
import com.histmersion.api.automation.agents.DoctorCheck
import com.histmersion.api.automation.agents.CheckLevel
import com.histmersion.util.problem.{Legacy, Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import com.histmersion.api.automation.Agents.ProviderKind
import com.histmersion.api.automation.ModelChains.*
import com.histmersion.api.automation.{Agents, AgentsMarkdown}
import com.histmersion.api.automation.ModelChains
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
class ModelChainsSuite extends munit.FunSuite:
  /** Does any of the problems match `p`? */
  private def has(problems: Problems)(p: PartialFunction[Problem, Boolean]): Boolean =
    problems.exists(p.applyOrElse(_, _ => false))

  private def unknownMeasure(unit: Problem.Raw): PartialFunction[Problem, Boolean] = {
    case Problem.Invalid.Unknown(t, raw, _, _) =>
      t == Problem.TypeName.named[Measure] && raw == unit
  }

  private val Sonnet = ModelRef(ProviderKind.ClaudeCode, ModelRef.Model("sonnet"))
  private val Qwen = ModelRef(ProviderKind.Local, ModelRef.Model("qwen2.5-coder:32b"))

  test("a chain is models separated by >, tried in order; one model is a chain of one; blank is none"):
    assertEquals(
      parseChain(ModelChains.Chain.Spec("claude-code:sonnet > local:qwen2.5-coder:32b")),
      Right(List(Sonnet, Qwen))
    )
    assertEquals(parseChain(ModelChains.Chain.Spec("claude-code:sonnet")), Right(List(Sonnet)))
    assertEquals(parseChain(ModelChains.Chain.Spec("  ")), Right(Nil))
    assertEquals(chainText(List(Sonnet, Qwen)), "claude-code:sonnet > local:qwen2.5-coder:32b")

  test("a chain reports every bad model and refuses a model named twice"):
    val e = parseChain(ModelChains.Chain.Spec("ollama:x > local: > claude-code:sonnet")).left.toOption.get
    assert(
      e.sentences.mkString("; ").contains("'ollama'") && e.sentences.mkString("; ").contains("model is required"),
      e
    )
    assert(
      parseModels(List(ModelRef.Model.Spec("claude-code:sonnet"), ModelRef.Model.Spec("claude-code:sonnet"))).left
        .exists(_.sentences.mkString("; ").contains("more than once"))
    )

  test("claude-code is a provider; its model ids are whatever the CLI accepts"):
    assertEquals(
      ModelRef.parse(ModelRef.Wire("claude-code:claude-sonnet-4-5")),
      Right(ModelRef(ProviderKind.ClaudeCode, ModelRef.Model("claude-sonnet-4-5")))
    )

  test("windows: names, n m|h|d|w, bounded to 1 minute .. 90 days, written back in the shortest exact unit"):
    assertEquals(api.automation.modelchains.Window.parse(LimitSpec.Window("5h")).map(_.seconds), Right(5 * 3600L))
    assertEquals(api.automation.modelchains.Window.parse(LimitSpec.Window("week")).map(_.text), Right("7d"))
    assertEquals(api.automation.modelchains.Window.parse(LimitSpec.Window("2w")).map(_.text), Right("14d"))
    assertEquals(api.automation.modelchains.Window.parse(LimitSpec.Window("month")).map(_.text), Right("30d"))
    assertEquals(api.automation.modelchains.Window.parse(LimitSpec.Window("90m")).map(_.text), Right("90m"))
    assert(api.automation.modelchains.Window.parse(LimitSpec.Window("0h")).isLeft)
    assert(api.automation.modelchains.Window.parse(LimitSpec.Window("91d")).isLeft)
    assert(
      api.automation.modelchains.Window
        .parse(LimitSpec.Window("fortnight"))
        .left
        .exists(has(_) { case Problem.Invalid.Unreadable(t, raw, _, _) =>
          t == Problem.TypeName.of(LimitSpec.Window) && raw == Problem.Raw("fortnight")
        })
    )

  test("limits: tokens with k/M suffixes, usd with a word or a dollar sign"):
    assertEquals(
      api.automation.modelchains.Limit.parse(api.automation.modelchains.Limit.Spec("500k tokens/5h")),
      Right(
        api.automation.modelchains.Limit(
          BigDecimal(500000),
          Measure.Tokens,
          api.automation.modelchains.Window(api.automation.modelchains.Window.Seconds(18000))
        )
      )
    )
    assertEquals(
      api.automation.modelchains.Limit.parse(api.automation.modelchains.Limit.Spec("2M tokens/week")).map(_.text),
      Right("2M tokens/7d")
    )
    assertEquals(
      api.automation.modelchains.Limit.parse(api.automation.modelchains.Limit.Spec("$5/day")),
      Right(
        api.automation.modelchains.Limit(
          BigDecimal(5),
          Measure.Usd,
          api.automation.modelchains.Window(api.automation.modelchains.Window.Seconds(86400))
        )
      )
    )
    assertEquals(
      api.automation.modelchains.Limit.parse(api.automation.modelchains.Limit.Spec("2.5 usd/day")).map(_.text),
      Right("2.5 usd/1d")
    )
    assertEquals(
      api.automation.modelchains.Limit.parse(api.automation.modelchains.Limit.Spec("1.5M tokens/5h")).map(_.amount),
      Right(BigDecimal(1500000))
    )

  test("limits: a fractional token count, a bad unit, no window, a non-positive amount are errors"):
    assert(
      api.automation.modelchains.Limit
        .parse(api.automation.modelchains.Limit.Spec("1.5 tokens/5h"))
        .left
        .exists(has(_) { case Problem.Invalid.Unreadable(t, raw, _, _) =>
          t == Problem.TypeName.of(LimitSpec.Amount) && raw == Problem.Raw("1.5")
        })
    )
    assert(
      api.automation.modelchains.Limit
        .parse(api.automation.modelchains.Limit.Spec("5 euros/day"))
        .left
        .exists(has(_)(unknownMeasure(Problem.Raw("euros"))))
    )
    assert(api.automation.modelchains.Limit.parse(api.automation.modelchains.Limit.Spec("5 usd")).isLeft)
    assert(
      api.automation.modelchains.Limit
        .parse(api.automation.modelchains.Limit.Spec("0 usd/day"))
        .left
        .exists(has(_) { case Problem.Invalid.Unreadable(t, raw, _, _) =>
          t == Problem.TypeName.of(LimitSpec.Amount) && raw == Problem.Raw("0")
        })
    )
    assert(
      api.automation.modelchains.Limit
        .fromSpec(LimitSpec(LimitSpec.Amount(-1), Measure.Usd, LimitSpec.Window("day")))
        .isLeft
    )
    assertEquals(
      api.automation.modelchains.Limit
        .fromSpec(LimitSpec(LimitSpec.Amount(500000), Measure.Tokens, LimitSpec.Window("5h")))
        .map(_.text),
      Right("500k tokens/5h")
    )

  test("allowances: the config grammar, provider-wide targets, a reserve"):
    val as = Allowance
      .parseAllowances(
        Allowance.Spec(
          "claude-code:* = 2M tokens/5h, 20M tokens/7d, reserve 20%; local:qwen2.5-coder:32b = 1 usd/day"
        )
      )
      .toOption
      .get
    assertEquals(as.map(_.target.wire), List("claude-code:*", "local:qwen2.5-coder:32b"))
    assertEquals(as.head.reservePct, 20)
    assertEquals(as.head.limits.map(_.text), List("2M tokens/5h", "20M tokens/7d"))
    assert(as.head.target.covers(Sonnet) && !as.head.target.covers(Qwen))
    assert(as(1).target.covers(Qwen))
    assertEquals(as.head.text, "2M tokens/5h, 20M tokens/7d, reserve 20%")
    assertEquals(Allowance.parseAllowances(Allowance.Spec("")), Right(Nil))

  test("allowances: every malformed entry is reported — a typo must not lift a cap"):
    val e = Allowance
      .parseAllowances(
        Allowance.Spec(
          "claude-code:* 2M tokens/5h; nobody:* = 1 usd/day; local:x = 3 bananas/day; local:y = 1 usd/day, reserve 120%"
        )
      )
      .left
      .toOption
      .get
    assert(
      has(e) { case Problem.Invalid.Unreadable(t, _, _, _) => t == Problem.TypeName.of(Allowance.Spec) },
      e
    )
    assert(
      has(e) { case Problem.Invalid.Unknown(t, raw, _, _) =>
        t == Problem.TypeName.of(ProviderKind.Wire) && raw == Problem.Raw("nobody")
      },
      e
    )
    assert(has(e)(unknownMeasure(Problem.Raw("bananas"))), e)
    assert(
      has(e) { case Problem.Invalid.OutOfRange(t, raw, _, _) =>
        t == Problem.TypeName.of(Allowance.ReservePct) && raw == Problem.Raw("120")
      },
      e
    )
    assert(
      Allowance
        .parseAllowances(Allowance.Spec("local:x = 1 usd/day; local:x = 2 usd/day"))
        .left
        .exists(_.sentences.mkString("; ").contains("more than once"))
    )
    assert(
      Allowance
        .parseAllowances(Allowance.Spec("local:x = 1 usd/day, 2 usd/day"))
        .left
        .exists(_.sentences.mkString("; ").contains("more than once"))
    )

  test("chains and allowances name a plugin provider only while it is registered"):
    assert(
      parseChain(ModelChains.Chain.Spec("gemini:pro > local:x")).left
        .exists(_.sentences.mkString("; ").contains("'gemini'"))
    )
    assertEquals(
      parseChain(
        ModelChains.Chain.Spec("gemini:pro > local:x"),
        Set(api.automation.ProviderKind.Plugin.Name("gemini"))
      ).map(_.map(_.wire)),
      Right(List("gemini:pro", "local:x"))
    )
    assert(
      Allowance
        .parseAllowances(Allowance.Spec("gemini:* = 1 usd/day"))
        .left
        .exists(_.sentences.mkString("; ").contains("'gemini'"))
    )
    val a = Allowance
      .parseAllowances(
        Allowance.Spec("gemini:* = 1 usd/day"),
        Set(api.automation.ProviderKind.Plugin.Name("gemini"))
      )
      .toOption
      .get
      .head
    assert(
      a.target.covers(
        ModelRef(
          ProviderKind.Plugin(api.automation.ProviderKind.Plugin.Name("gemini")),
          ModelRef.Model("pro")
        )
      )
    )
    assert(!a.target.covers(ModelRef(ProviderKind.Local, ModelRef.Model("pro"))))
    assertEquals(
      api.automation.modelchains.Target
        .parse(
          api.automation.modelchains.Target.Spec("gemini:pro"),
          Set(api.automation.ProviderKind.Plugin.Name("gemini"))
        )
        .map(_.wire),
      Right("gemini:pro")
    )

  private val H = 3600L * 1000L

  test("a limit counts only its rolling window; usable while used < amount − reserve"):
    val l = api.automation.modelchains.Limit(
      BigDecimal(1000),
      Measure.Tokens,
      api.automation.modelchains.Window(api.automation.modelchains.Window.Seconds(3600))
    )
    val now = 10 * H
    val spends = List(
      Spend(Spend.At(now - 2 * H), Spend.Tokens(900), 0),
      Spend(Spend.At(now - H / 2), Spend.Tokens(600), 0),
      Spend(Spend.At(now - H / 4), Spend.Tokens(100), 0)
    )
    val s = l.limitState(Allowance.ReservePct(0), spends, LimitState.Now(now))
    assertEquals(s.used, BigDecimal(700)) // the one two hours ago has left the window
    assertEquals(s.remaining, BigDecimal(300))
    assert(s.usable.usable)
    // a 40% reserve: 400 must stay, so 700 used is over the line
    val r = l.limitState(Allowance.ReservePct(40), spends, LimitState.Now(now))
    assertEquals(r.reserve, BigDecimal(400))
    assert(!r.usable.usable)

  test("freesAt: when enough of the window's oldest spend has aged out"):
    val l = api.automation.modelchains.Limit(
      BigDecimal(1000),
      Measure.Tokens,
      api.automation.modelchains.Window(api.automation.modelchains.Window.Seconds(3600))
    )
    val now = 10 * H
    val spends = List(
      Spend(Spend.At(now - 50 * 60000L), Spend.Tokens(600), 0),
      Spend(Spend.At(now - 10 * 60000L), Spend.Tokens(500), 0)
    )
    val s = l.limitState(Allowance.ReservePct(0), spends, LimitState.Now(now))
    assert(!s.usable.usable)
    // the 600 leaves the window 10 minutes from now: 500 < 1000 then
    assertEquals(s.freesAt, Some(now + 10 * 60000L))
    // with a 60% reserve (threshold 400) the 500 must leave too: 50 minutes from now
    assertEquals(
      l.limitState(Allowance.ReservePct(60), spends, LimitState.Now(now)).freesAt,
      Some(now + 50 * 60000L)
    )
    assertEquals(s.headroom, BigDecimal(0))

  test("usd limits sum cost; verdict needs every limit, frees at the latest; headroom is the least left"):
    val day = api.automation.modelchains.Limit(
      BigDecimal(5),
      Measure.Usd,
      api.automation.modelchains.Window(api.automation.modelchains.Window.Seconds(86400))
    )
    val hour = api.automation.modelchains.Limit(
      BigDecimal(80000),
      Measure.Tokens,
      api.automation.modelchains.Window(api.automation.modelchains.Window.Seconds(3600))
    )
    val now = 100 * H
    val spends = List(
      Spend(Spend.At(now - 20 * H), Spend.Tokens(10), BigDecimal("4.5")),
      Spend(Spend.At(now - 30 * 60000L), Spend.Tokens(90000), BigDecimal("0.6"))
    )
    val ss = List(
      day.limitState(Allowance.ReservePct(0), spends, LimitState.Now(now)),
      hour.limitState(Allowance.ReservePct(0), spends, LimitState.Now(now))
    )
    assert(!ss.head.usable.usable && !ss(1).usable.usable)
    val (ok, frees) = verdict(ss)
    assert(!ok)
    assertEquals(frees, Some(now - 20 * H + 86400L * 1000L), "the day limit frees last")
    val fine = List(
      day.limitState(Allowance.ReservePct(0), Nil, LimitState.Now(now)),
      hour
        .modify(_.amount)
        .setTo(BigDecimal(100000))
        .limitState(
          Allowance.ReservePct(10),
          List(Spend(Spend.At(now - 1000), Spend.Tokens(50000), 0)),
          LimitState.Now(now)
        )
    )
    assertEquals(verdict(fine), (true, None))
    assertEquals(headroom(fine), (Some(40000L), Some(BigDecimal(5))))

  test("a reserve as large as the limit never frees by itself"):
    val l = api.automation.modelchains.Limit(
      BigDecimal(10),
      Measure.Usd,
      api.automation.modelchains.Window(api.automation.modelchains.Window.Seconds(3600))
    )
    val s = l.limitState(
      Allowance.ReservePct(99),
      List(Spend(Spend.At(1000), Spend.Tokens(0), BigDecimal("0.2"))),
      LimitState.Now(2000)
    )
    assert(!s.usable.usable)
    assertEquals(s.freesAt, Some(1000 + 3600L * 1000L)) // 0 < 0.1 once the spend leaves
    assertEquals(verdict(List(s.modify(_.freesAt).setTo(None))), (false, None))

  test("Markdown: the chains with the model a call would use, the allowances with use and reset, the doctor"):
    import com.histmersion.api.automation.Agents.*
    val m = ModelsInfo(
      enabled = ModelsInfo.Enabled.Enabled,
      Nil,
      List(ModelsInfo.Pipelines("enrich-source")),
      ModelsInfo.MaxConcurrentRuns(4),
      Nil,
      chains = List(
        RoleChain(
          RoleChain.Role("extractor"),
          SettingSource.Wire("saved"),
          List(
            ChainEntry(
              ChainEntry.Model("claude-code:sonnet"),
              ready = ChainEntry.Ready.Ready,
              usable = ChainEntry.Usable.Unusable,
              List(Problem.Unclassified(Legacy.Text("below its allowance"))),
              Some(LimitStatus.FreesAt("2026-10-05T14:00:00Z"))
            ),
            ChainEntry(
              ChainEntry.Model("local:qwen"),
              ready = ChainEntry.Ready.Ready,
              usable = ChainEntry.Usable.Usable
            )
          ),
          Some(RoleChain.Active("local:qwen"))
        )
      ),
      allowances = List(
        AllowanceStatus(
          AllowanceStatus.Target("claude-code:*"),
          SettingSource.Wire("config"),
          AllowanceStatus.ReservePct(20),
          List(
            LimitStatus(
              LimitStatus.Limit("2M tokens/5h"),
              Measure.Tokens,
              LimitStatus.Window("5h"),
              LimitStatus.Amount(2000000),
              LimitStatus.Used(1900000),
              LimitStatus.Remaining(100000),
              LimitStatus.Reserve(400000),
              usable = LimitStatus.Usable.Unusable,
              Some(LimitStatus.FreesAt("2026-10-05T14:00:00Z"))
            )
          ),
          usable = AllowanceStatus.Usable.Unusable,
          Some(LimitStatus.FreesAt("2026-10-05T14:00:00Z")),
          Some(AllowanceStatus.Note("counts what claude -p reported"))
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
      m.modify(_.chains)
        .setTo(
          List(
            RoleChain(
              RoleChain.Role("writer"),
              SettingSource.Wire("config"),
              Nil,
              None,
              List(Problem.Unclassified(Legacy.Text("no model")))
            )
          )
        )
        .modify(_.allowances)
        .setTo(Nil)
    )
    assert(none.contains("| writer | — | no model | config |") && none.contains("No allowance"), none)
    val d = DoctorReport(
      enabled = DoctorReport.Enabled.Disabled,
      online = DoctorReport.Online.Offline,
      List(
        ProviderDoctor(
          ProviderDoctor.Provider("claude-code"),
          used = ProviderDoctor.Used.Used,
          List(ProviderDoctor.Models("claude-code:sonnet")),
          List(
            DoctorCheck(
              DoctorCheck.Name("login"),
              CheckLevel.Ok,
              DoctorCheck.Detail("logged in (claude.ai, plan max)")
            ),
            DoctorCheck(DoctorCheck.Name("bind"), CheckLevel.Fail, DoctorCheck.Detail("refused"))
          )
        )
      ),
      List(DoctorCheck(DoctorCheck.Name("agents"), CheckLevel.Warn, DoctorCheck.Detail("off")))
    )
    val dm = AgentsMarkdown.doctor(d)
    assert(
      dm.contains("# Agent doctor (offline)") && dm.contains(
        "## claude-code — **FAIL** (used by: claude-code:sonnet)"
      ) &&
        dm.contains("- **warn** · agents: off"),
      dm
    )
