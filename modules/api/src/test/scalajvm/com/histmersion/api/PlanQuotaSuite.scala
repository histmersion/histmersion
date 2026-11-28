package com.histmersion
package api

import com.histmersion.api.automation.planquota.Reading
import com.histmersion.api.automation.planquota.Rules
import com.histmersion.api.automation.planquota.Floors
import com.histmersion.api.automation.planquota.Channel
import com.histmersion.api.automation.planquota.PoolStatus
import com.histmersion.api.automation.planquota.Verdict
import com.histmersion.api.automation.modelchains.ChainEntry
import com.histmersion.api.automation.planquota.Logins
import com.histmersion.api.automation.agents.ModelRef
import com.histmersion.api.automation.planquota.PoolId
import com.histmersion.api.automation.planquota.Login
import com.histmersion.api.automation.planquota.RefreshNeed
import com.histmersion.api.automation.planquota.RefreshInfo
import com.histmersion.api.automation.planquota.RefreshMode

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Agents.ProviderKind
import com.histmersion.api.automation.PlanQuota
import com.histmersion.api.automation.PlanQuota.*
import com.histmersion.api.automation.ModelChains
import com.histmersion.lint.{tolerated, LintRule}

/** Plan floors (docs/research-plan-quotas.md §5): the pick with plan readings — floors, a provider refusal, a missing
  * window, a stale reading, a hold, a window whose reset passed.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.SilentInterp, "pending G35 (a vocabulary renders through its wire string)")
class PlanQuotaSuite extends munit.FunSuite:

  private val Now = 1_000_000_000L
  private val Min = 60_000L
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val NowAt = PlanQuota.Now(Now)
  private val FiveHour = Reading.Kind("five_hour")
  private val SevenDay = Reading.Kind("seven_day")
  private val Window10080 = Reading.Kind("10080m")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val Rules0 = Rules(
    Floors(
      Map(
        Floors.ByKindKey("five_hour") -> Floors.Floor(25),
        Floors.ByKindKey("seven_day") -> Floors.Floor(25),
        Floors.ByKindKey("codex/10080m") -> Floors.Floor(30)
      )
    ),
    Rules.DefaultFloor(10),
    Rules.StaleMs(10 * Min),
    Rules.MarginPct(10)
  )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def r(
      kind: Reading.Kind,
      used: Reading.UsedPct,
      reset: Reading.ResetsAt = Reading.ResetsAt(Now + 60 * Min),
      seen: Reading.ObservedAt = Reading.ObservedAt(Now - Min),
      rejected: Reading.Rejected = Reading.Rejected.Accepted,
      limit: Reading.Limit = Reading.Limit("claude"),
      model: Option[Reading.Model] = None
  ) =
    Reading(limit, kind, used, Some(reset), rejected, seen, Channel.Run, model)

  test("floors: per window kind, limit-qualified first, a default for the rest; typos reported"):
    val f = Floors.parse(Floors.Wire("five_hour=30, seven_day = 25%, codex/10080m=20")).toOption.get
    assertEquals(
      f.of(Floors.OfLimit("claude"), Floors.OfKind("five_hour"), Rules.DefaultFloor(10)),
      30
    )
    assertEquals(
      f.of(Floors.OfLimit("codex"), Floors.OfKind("10080m"), Rules.DefaultFloor(10)),
      20
    )
    assertEquals(
      f.of(
        Floors.OfLimit("gpt-reserve"),
        Floors.OfKind("10080m"),
        Rules.DefaultFloor(10)
      ),
      10
    )
    assert(
      Floors.parse(Floors.Wire("five_hour=130")).isLeft && Floors
        .parse(Floors.Wire("five_hour"))
        .isLeft && Floors.parse(Floors.Wire("=3")).isLeft
    )
    assertEquals(Floors.parse(Floors.Wire("")).map(_.byKind), Right(Map.empty))
    assertEquals(
      Floors.parse(Floors.Wire("five_hour=130, =3, seven_day")).left.map(_.toVector),
      Left(
        Vector(
          Problem.Invalid.OutOfRange(
            Problem.TypeName("Floors.Floor"),
            Problem.Raw("130"),
            Problem.Invalid.Bounds(Some(Problem.Raw("0")), Some(Problem.Raw("100"))),
            Vector(Problem.Field("five_hour=130"))
          ),
          Problem.Invalid.Missing(Problem.Field("window"), Vector(Problem.Field("=3"))),
          Problem.Invalid.Missing(Problem.Field("percent"), Vector(Problem.Field("seven_day")))
        )
      ),
      "every item's problem, each at its item"
    )
    assertEquals(f.text, "codex/10080m=20, five_hour=30, seven_day=25")

  test("usable only while every window keeps MORE than its floor left"):
    val (ok, _) = verdict(
      List(r(FiveHour, Reading.UsedPct(70)), r(SevenDay, Reading.UsedPct(74))),
      held = PoolStatus.Held.Released,
      None,
      Rules0,
      NowAt
    )
    assert(ok.usable, ok)
    val (low, states) = verdict(
      List(r(FiveHour, Reading.UsedPct(75)), r(SevenDay, Reading.UsedPct(10))),
      held = PoolStatus.Held.Released,
      None,
      Rules0,
      NowAt
    )
    low match
      case Verdict.Blocked(why, until) =>
        assert(s"$why".contains("five_hour: 25% left, floor 25%"), why)
        assertEquals(until, Some(Now + 60 * Min), "until the blocking window resets")
      case other => fail(s"not blocked: $other")
    assertEquals(states.map(_.usable.usable), List(false, true))

  test("the earliest reset among the blocking windows is when the run is looked at again"):
    val (v, _) = verdict(
      List(
        r(FiveHour, Reading.UsedPct(90), reset = Reading.ResetsAt(Now + 30 * Min)),
        r(SevenDay, Reading.UsedPct(95), reset = Reading.ResetsAt(Now + 3000 * Min))
      ),
      held = PoolStatus.Held.Released,
      None,
      Rules0,
      NowAt
    )
    assertEquals(
      v,
      Verdict.Blocked(v.asInstanceOf[Verdict.Blocked].reason, Some(Verdict.Blocked.Until(Now + 30 * Min)))
    )

  test("a provider refusal blocks whatever the percentages say; a rejected window too"):
    val (v1, _) =
      verdict(
        List(r(FiveHour, Reading.UsedPct(1))),
        held = PoolStatus.Held.Released,
        Some((Rationale("ordinary usage not allowed"), None)),
        Rules0,
        NowAt
      )
    assertEquals(v1, Verdict.Blocked(Rationale("ordinary usage not allowed"), None))
    val (v2, _) = verdict(
      List(r(Window10080, Reading.UsedPct(3), rejected = Reading.Rejected.Rejected, limit = Reading.Limit("codex"))),
      held = PoolStatus.Held.Released,
      None,
      Rules0,
      NowAt
    )
    assert(!v2.usable)
    val (v3, _) = verdict(
      Nil,
      held = PoolStatus.Held.Released,
      Some((Rationale("gone"), Some(Verdict.Blocked.Until(Now - 1)))),
      Rules0,
      NowAt
    )
    assert(v3.usable, "a refusal whose time passed no longer blocks")

  test("the hold switch wins over everything"):
    val (v, _) =
      verdict(List(r(FiveHour, Reading.UsedPct(0))), held = PoolStatus.Held.Held, None, Rules0, NowAt)
    assert(v match { case Verdict.Blocked(w, None) => s"$w".contains("held"); case _ => false }, v)

  test("no reading is unknown: usable, said so; a window whose reset passed has started over"):
    assertEquals(
      verdict(Nil, held = PoolStatus.Held.Released, None, Rules0, NowAt)._1,
      Verdict.Usable(Some(Verdict.Usable.Note("no reading yet: unknown until a run reads it")))
    )
    val (v, states) = verdict(
      List(r(FiveHour, Reading.UsedPct(99), reset = Reading.ResetsAt(Now - 1))),
      held = PoolStatus.Held.Released,
      None,
      Rules0,
      NowAt
    )
    assert(v.usable && states.isEmpty, (v, states))

  test("a stale reading lets a run go on only with the margin to spare"):
    val old = Reading.ObservedAt(Now - 60 * Min)
    assert(
      verdict(
        List(r(FiveHour, Reading.UsedPct(60), seen = old)),
        held = PoolStatus.Held.Released,
        None,
        Rules0,
        NowAt
      )._1.usable,
      "40% left ≥ 25 + 10"
    )
    val (v, _) = verdict(
      List(r(FiveHour, Reading.UsedPct(70), seen = old)),
      held = PoolStatus.Held.Released,
      None,
      Rules0,
      NowAt
    )
    assert(!v.usable && s"${v.asInstanceOf[Verdict.Blocked].reason}".contains("stale"), v)

  test("newest reading per window; a fallback model's bucket counts only for that model"):
    val older = r(FiveHour, Reading.UsedPct(99), seen = Reading.ObservedAt(Now - 5 * Min))
    val newer = r(FiveHour, Reading.UsedPct(10), seen = Reading.ObservedAt(Now - Min))
    assert(verdict(List(older, newer), held = PoolStatus.Held.Released, None, Rules0, NowAt)._1.usable)
    val reserve = r(
      Window10080,
      Reading.UsedPct(100),
      limit = Reading.Limit("gpt-reserve"),
      model = Some(Reading.Model("gpt-5.6-luna"))
    )
    assert(
      verdict(
        List(reserve),
        held = PoolStatus.Held.Released,
        None,
        Rules0,
        NowAt,
        Some(ChainEntry.Model("gpt-5.6"))
      )._1.usable
    )
    assert(
      !verdict(
        List(reserve),
        held = PoolStatus.Held.Released,
        None,
        Rules0,
        NowAt,
        Some(ChainEntry.Model("gpt-5.6-luna"))
      )._1.usable
    )

  test("pools: claude-code the Claude plan, codex the ChatGPT plan, opencode openai/… only on a ChatGPT login"):
    val (plain, oc) = (Logins(), Logins(opencodeChatgpt = Logins.OpencodeChatgpt.Chatgpt))
    assertEquals(poolOf(ModelRef(ProviderKind.ClaudeCode, ModelRef.Model("opus")), plain), Some(PoolId.ClaudePlan))
    assertEquals(poolOf(ModelRef(ProviderKind.Codex, ModelRef.Model("gpt-5")), plain), Some(PoolId.ChatgptPlan))
    assertEquals(poolOf(ModelRef(ProviderKind.Opencode, ModelRef.Model("openai/gpt-5")), oc), Some(PoolId.ChatgptPlan))
    assertEquals(poolOf(ModelRef(ProviderKind.Opencode, ModelRef.Model("openai/gpt-5")), plain), None)
    assertEquals(poolOf(ModelRef(ProviderKind.Anthropic, ModelRef.Model("claude-x")), oc), None)
    assert(
      paid(ModelRef(ProviderKind.Anthropic, ModelRef.Model("x")), plain) && paid(
        ModelRef(ProviderKind.Opencode, ModelRef.Model("zen/x")),
        oc
      )
    )
    assert(
      !paid(ModelRef(ProviderKind.Local, ModelRef.Model("x")), plain) && !paid(
        ModelRef(ProviderKind.ClaudeCode, ModelRef.Model("x")),
        plain
      )
    )

  test("pools: a CLI set to use an API key draws on no plan — it bills the API, so it is paid"):
    val keyed = Logins(
      claudeCodeApiKey = Logins.ClaudeCodeApiKey.ApiKey,
      codexApiKey = Logins.CodexApiKey.ApiKey
    )
    assertEquals(poolOf(ModelRef(ProviderKind.ClaudeCode, ModelRef.Model("opus")), keyed), None)
    assertEquals(poolOf(ModelRef(ProviderKind.Codex, ModelRef.Model("gpt-5")), keyed), None)
    assert(
      paid(ModelRef(ProviderKind.ClaudeCode, ModelRef.Model("opus")), keyed) && paid(
        ModelRef(ProviderKind.Codex, ModelRef.Model("gpt-5")),
        keyed
      )
    )
    // the other CLI keeps its plan
    assertEquals(
      poolOf(
        ModelRef(ProviderKind.Codex, ModelRef.Model("gpt-5")),
        Logins(claudeCodeApiKey = Logins.ClaudeCodeApiKey.ApiKey)
      ),
      Some(PoolId.ChatgptPlan)
    )

  // ── a fresh reading before pausing (#989) ─────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val Old = Reading.ObservedAt(Now - 60 * Min) // a reading older than stale-minutes (10)
  private val In = Login.Present(Login.Present.Detail("logged in"))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
  private def decide(
      need: Option[RefreshNeed],
      login: Login = In,
      enabled: Boolean = true,
      last: Option[RefreshInfo.LastAttempt] = None,
      force: Boolean = false
  ) = refreshDecision(need, login, enabled, last, Rules.MinGap(10 * Min), NowAt, force)

  test("#989: a pool paused ONLY because its reading is stale wants a fresh one (49% left, floor 40%, margin 10)"):
    val rules = Rules0.modify(_.floors).setTo(Floors(Map(Floors.ByKindKey("seven_day") -> Floors.Floor(40))))
    val stale = List(r(SevenDay, Reading.UsedPct(51), seen = Old))
    assert(
      !verdict(stale, held = PoolStatus.Held.Released, None, rules, NowAt)._1.usable,
      "49 < 40 + 10: paused on the stale reading"
    )
    assertEquals(
      refreshNeed(stale, held = PoolStatus.Held.Released, None, rules, NowAt),
      Some(RefreshNeed.StaleOnly)
    )
    assertEquals(decide(Some(RefreshNeed.StaleOnly)), Right(Some(RefreshNeed.StaleOnly)))

  test("#989: below its floor on a FRESH reading pauses without a refresh; stale AND below the floor too"):
    assertEquals(
      refreshNeed(
        List(r(FiveHour, Reading.UsedPct(80))),
        held = PoolStatus.Held.Released,
        None,
        Rules0,
        NowAt
      ),
      None
    )
    assertEquals(
      refreshNeed(
        List(r(FiveHour, Reading.UsedPct(80), seen = Old)),
        held = PoolStatus.Held.Released,
        None,
        Rules0,
        NowAt
      ),
      None,
      "20% left is below the floor whatever its age"
    )
    // one window below its floor on a fresh reading, another only stale: a fresh reading would not help
    assertEquals(
      refreshNeed(
        List(r(FiveHour, Reading.UsedPct(80)), r(SevenDay, Reading.UsedPct(70), seen = Old)),
        held = PoolStatus.Held.Released,
        None,
        Rules0,
        NowAt
      ),
      None
    )
    assertEquals(decide(None).left.map(w => s"$w".contains("decides by itself")), Left(true))

  test("#989: a usable pool, a hold, a refusal, a rejected window: no refresh"):
    assertEquals(
      refreshNeed(
        List(r(FiveHour, Reading.UsedPct(10), seen = Old)),
        held = PoolStatus.Held.Released,
        None,
        Rules0,
        NowAt
      ),
      None,
      "stale but usable"
    )
    val stale = List(r(FiveHour, Reading.UsedPct(70), seen = Old))
    assertEquals(refreshNeed(stale, held = PoolStatus.Held.Held, None, Rules0, NowAt), None)
    assertEquals(
      refreshNeed(
        stale,
        held = PoolStatus.Held.Released,
        Some((Rationale("no"), None)),
        Rules0,
        NowAt
      ),
      None
    )
    assertEquals(
      refreshNeed(
        stale,
        held = PoolStatus.Held.Released,
        Some((Rationale("no"), Some(Verdict.Blocked.Until(Now - 1)))),
        Rules0,
        NowAt
      ),
      Some(RefreshNeed.StaleOnly),
      "a refusal that has lifted does not count"
    )
    assertEquals(
      refreshNeed(
        List(r(FiveHour, Reading.UsedPct(70), seen = Old, rejected = Reading.Rejected.Rejected)),
        held = PoolStatus.Held.Released,
        None,
        Rules0,
        NowAt
      ),
      None
    )

  test("#989: no reading at all (or only windows whose reset passed) — read before the first use"):
    assertEquals(
      refreshNeed(Nil, held = PoolStatus.Held.Released, None, Rules0, NowAt),
      Some(RefreshNeed.NoReading)
    )
    assertEquals(
      refreshNeed(
        List(r(FiveHour, Reading.UsedPct(99), reset = Reading.ResetsAt(Now - 1))),
        held = PoolStatus.Held.Released,
        None,
        Rules0,
        NowAt
      ),
      Some(RefreshNeed.NoReading)
    )
    assertEquals(decide(Some(RefreshNeed.NoReading)), Right(Some(RefreshNeed.NoReading)))

  test("#989: throttled to once per stale-minutes; a person's refresh is not, but never without a login"):
    assert(
      decide(Some(RefreshNeed.StaleOnly), last = Some(RefreshInfo.LastAttempt(Now - 9 * Min))).left
        .exists(w => s"$w".contains("less than stale-minutes"))
    )
    assertEquals(
      decide(Some(RefreshNeed.StaleOnly), last = Some(RefreshInfo.LastAttempt(Now - 10 * Min))),
      Right(Some(RefreshNeed.StaleOnly))
    )
    assert(
      throttled(Some(RefreshInfo.LastAttempt(Now - Min)), Rules.MinGap(10 * Min), NowAt) && !throttled(
        None,
        Rules.MinGap(10 * Min),
        NowAt
      )
    )
    assertEquals(
      decide(None, last = Some(RefreshInfo.LastAttempt(Now - Min)), force = true),
      Right(None),
      "forced: no need, no throttle"
    )
    assert(
      decide(None, login = Login.Missing(Rationale("not logged in")), force = true).left
        .exists(w => s"$w".startsWith("no login"))
    )

  test("#989: no login means no refresh; an unknown login does not stop one; refresh off is off"):
    assert(
      decide(Some(RefreshNeed.NoReading), login = Login.Missing(Rationale("x"))).left
        .exists(w => s"$w".contains("no login: x"))
    )
    assertEquals(
      decide(Some(RefreshNeed.NoReading), login = Login.Unknown(Rationale("timeout"))),
      Right(Some(RefreshNeed.NoReading))
    )
    assert(decide(Some(RefreshNeed.StaleOnly), enabled = false).left.exists(w => s"$w".contains("off")))
    assertEquals(RefreshMode.parse(RefreshMode.Wire(" Probe ")), Some(RefreshMode.Probe))
    assertEquals(RefreshMode.parse(RefreshMode.Wire("sometimes")), None)
    assertEquals(Channel.parse(Channel.Wire("probe")), Some(Channel.Probe))
