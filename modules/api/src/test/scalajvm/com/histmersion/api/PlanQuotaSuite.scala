package com.histmersion.api

import com.histmersion.api.automation.Agents.{ModelRef, ProviderKind}
import com.histmersion.api.automation.PlanQuota.*

/** Plan floors (docs/research-plan-quotas.md §5): the pick with plan readings — floors, a provider refusal, a missing
  * window, a stale reading, a hold, a window whose reset passed.
  */
class PlanQuotaSuite extends munit.FunSuite:

  private val Now = 1_000_000_000L
  private val Min = 60_000L
  private val Rules0 = Rules(Floors(Map("five_hour" -> 25, "seven_day" -> 25, "codex/10080m" -> 30)), 10, 10 * Min, 10)
  private def r(
      kind: String,
      used: Double,
      reset: Long = Now + 60 * Min,
      seen: Long = Now - Min,
      rejected: Boolean = false,
      limit: String = "claude",
      model: Option[String] = None
  ) =
    Reading(limit, kind, used, Some(reset), rejected, seen, Source.Run, model)

  test("floors: per window kind, limit-qualified first, a default for the rest; typos reported"):
    val f = Floors.parse("five_hour=30, seven_day = 25%, codex/10080m=20").toOption.get
    assertEquals(f.of("claude", "five_hour", 10), 30)
    assertEquals(f.of("codex", "10080m", 10), 20)
    assertEquals(f.of("gpt-reserve", "10080m", 10), 10)
    assert(Floors.parse("five_hour=130").isLeft && Floors.parse("five_hour").isLeft && Floors.parse("=3").isLeft)
    assertEquals(Floors.parse("").map(_.byKind), Right(Map.empty))
    assertEquals(f.text, "codex/10080m=20, five_hour=30, seven_day=25")

  test("usable only while every window keeps MORE than its floor left"):
    val (ok, _) = verdict(List(r("five_hour", 70), r("seven_day", 74)), held = false, None, Rules0, Now)
    assert(ok.usable, ok)
    val (low, states) = verdict(List(r("five_hour", 75), r("seven_day", 10)), held = false, None, Rules0, Now)
    low match
      case Verdict.Blocked(why, until) =>
        assert(why.contains("five_hour: 25% left, floor 25%"), why)
        assertEquals(until, Some(Now + 60 * Min), "until the blocking window resets")
      case other => fail(s"not blocked: $other")
    assertEquals(states.map(_.usable), List(false, true))

  test("the earliest reset among the blocking windows is when the run is looked at again"):
    val (v, _) = verdict(
      List(r("five_hour", 90, reset = Now + 30 * Min), r("seven_day", 95, reset = Now + 3000 * Min)),
      held = false,
      None,
      Rules0,
      Now
    )
    assertEquals(v, Verdict.Blocked(v.asInstanceOf[Verdict.Blocked].reason, Some(Now + 30 * Min)))

  test("a provider refusal blocks whatever the percentages say; a rejected window too"):
    val (v1, _) =
      verdict(List(r("five_hour", 1)), held = false, Some(("ordinary usage not allowed", None)), Rules0, Now)
    assertEquals(v1, Verdict.Blocked("ordinary usage not allowed", None))
    val (v2, _) = verdict(List(r("10080m", 3, rejected = true, limit = "codex")), held = false, None, Rules0, Now)
    assert(!v2.usable)
    val (v3, _) = verdict(Nil, held = false, Some(("gone", Some(Now - 1))), Rules0, Now)
    assert(v3.usable, "a refusal whose time passed no longer blocks")

  test("the hold switch wins over everything"):
    val (v, _) = verdict(List(r("five_hour", 0)), held = true, None, Rules0, Now)
    assert(v match { case Verdict.Blocked(w, None) => w.contains("held"); case _ => false }, v)

  test("no reading is unknown: usable, said so; a window whose reset passed has started over"):
    assertEquals(
      verdict(Nil, held = false, None, Rules0, Now)._1,
      Verdict.Usable(Some("no reading yet: unknown until a run reads it"))
    )
    val (v, states) = verdict(List(r("five_hour", 99, reset = Now - 1)), held = false, None, Rules0, Now)
    assert(v.usable && states.isEmpty, (v, states))

  test("a stale reading lets a run go on only with the margin to spare"):
    val old = Now - 60 * Min
    assert(
      verdict(List(r("five_hour", 60, seen = old)), held = false, None, Rules0, Now)._1.usable,
      "40% left ≥ 25 + 10"
    )
    val (v, _) = verdict(List(r("five_hour", 70, seen = old)), held = false, None, Rules0, Now)
    assert(!v.usable && v.asInstanceOf[Verdict.Blocked].reason.contains("stale"), v)

  test("newest reading per window; a fallback model's bucket counts only for that model"):
    val older = r("five_hour", 99, seen = Now - 5 * Min)
    val newer = r("five_hour", 10, seen = Now - Min)
    assert(verdict(List(older, newer), held = false, None, Rules0, Now)._1.usable)
    val reserve = r("10080m", 100, limit = "gpt-reserve", model = Some("gpt-5.6-luna"))
    assert(verdict(List(reserve), held = false, None, Rules0, Now, Some("gpt-5.6"))._1.usable)
    assert(!verdict(List(reserve), held = false, None, Rules0, Now, Some("gpt-5.6-luna"))._1.usable)

  test("pools: claude-code the Claude plan, codex the ChatGPT plan, opencode openai/… only on a ChatGPT login"):
    val (plain, oc) = (Logins(), Logins(opencodeChatgpt = true))
    assertEquals(poolOf(ModelRef(ProviderKind.ClaudeCode, "opus"), plain), Some(PoolId.ClaudePlan))
    assertEquals(poolOf(ModelRef(ProviderKind.Codex, "gpt-5"), plain), Some(PoolId.ChatgptPlan))
    assertEquals(poolOf(ModelRef(ProviderKind.Opencode, "openai/gpt-5"), oc), Some(PoolId.ChatgptPlan))
    assertEquals(poolOf(ModelRef(ProviderKind.Opencode, "openai/gpt-5"), plain), None)
    assertEquals(poolOf(ModelRef(ProviderKind.Anthropic, "claude-x"), oc), None)
    assert(paid(ModelRef(ProviderKind.Anthropic, "x"), plain) && paid(ModelRef(ProviderKind.Opencode, "zen/x"), oc))
    assert(!paid(ModelRef(ProviderKind.Local, "x"), plain) && !paid(ModelRef(ProviderKind.ClaudeCode, "x"), plain))

  test("pools: a CLI set to use an API key draws on no plan — it bills the API, so it is paid"):
    val keyed = Logins(claudeCodeApiKey = true, codexApiKey = true)
    assertEquals(poolOf(ModelRef(ProviderKind.ClaudeCode, "opus"), keyed), None)
    assertEquals(poolOf(ModelRef(ProviderKind.Codex, "gpt-5"), keyed), None)
    assert(paid(ModelRef(ProviderKind.ClaudeCode, "opus"), keyed) && paid(ModelRef(ProviderKind.Codex, "gpt-5"), keyed))
    // the other CLI keeps its plan
    assertEquals(
      poolOf(ModelRef(ProviderKind.Codex, "gpt-5"), Logins(claudeCodeApiKey = true)),
      Some(PoolId.ChatgptPlan)
    )

  // ── a fresh reading before pausing (#989) ─────────────────────────────────────────────────────

  private val Old = Now - 60 * Min // a reading older than stale-minutes (10)
  private val In = Login.Present("logged in")
  private def decide(
      need: Option[RefreshNeed],
      login: Login = In,
      enabled: Boolean = true,
      last: Option[Long] = None,
      force: Boolean = false
  ) = refreshDecision(need, login, enabled, last, 10 * Min, Now, force)

  test("#989: a pool paused ONLY because its reading is stale wants a fresh one (49% left, floor 40%, margin 10)"):
    val rules = Rules0.copy(floors = Floors(Map("seven_day" -> 40)))
    val stale = List(r("seven_day", 51, seen = Old))
    assert(!verdict(stale, held = false, None, rules, Now)._1.usable, "49 < 40 + 10: paused on the stale reading")
    assertEquals(refreshNeed(stale, held = false, None, rules, Now), Some(RefreshNeed.StaleOnly))
    assertEquals(decide(Some(RefreshNeed.StaleOnly)), Right(Some(RefreshNeed.StaleOnly)))

  test("#989: below its floor on a FRESH reading pauses without a refresh; stale AND below the floor too"):
    assertEquals(refreshNeed(List(r("five_hour", 80)), held = false, None, Rules0, Now), None)
    assertEquals(
      refreshNeed(List(r("five_hour", 80, seen = Old)), held = false, None, Rules0, Now),
      None,
      "20% left is below the floor whatever its age"
    )
    // one window below its floor on a fresh reading, another only stale: a fresh reading would not help
    assertEquals(
      refreshNeed(List(r("five_hour", 80), r("seven_day", 70, seen = Old)), held = false, None, Rules0, Now),
      None
    )
    assertEquals(decide(None).left.map(_.contains("decides by itself")), Left(true))

  test("#989: a usable pool, a hold, a refusal, a rejected window: no refresh"):
    assertEquals(
      refreshNeed(List(r("five_hour", 10, seen = Old)), held = false, None, Rules0, Now),
      None,
      "stale but usable"
    )
    val stale = List(r("five_hour", 70, seen = Old))
    assertEquals(refreshNeed(stale, held = true, None, Rules0, Now), None)
    assertEquals(refreshNeed(stale, held = false, Some(("no", None)), Rules0, Now), None)
    assertEquals(
      refreshNeed(stale, held = false, Some(("no", Some(Now - 1))), Rules0, Now),
      Some(RefreshNeed.StaleOnly),
      "a refusal that has lifted does not count"
    )
    assertEquals(
      refreshNeed(List(r("five_hour", 70, seen = Old, rejected = true)), held = false, None, Rules0, Now),
      None
    )

  test("#989: no reading at all (or only windows whose reset passed) — read before the first use"):
    assertEquals(refreshNeed(Nil, held = false, None, Rules0, Now), Some(RefreshNeed.NoReading))
    assertEquals(
      refreshNeed(List(r("five_hour", 99, reset = Now - 1)), held = false, None, Rules0, Now),
      Some(RefreshNeed.NoReading)
    )
    assertEquals(decide(Some(RefreshNeed.NoReading)), Right(Some(RefreshNeed.NoReading)))

  test("#989: throttled to once per stale-minutes; a person's refresh is not, but never without a login"):
    assert(
      decide(Some(RefreshNeed.StaleOnly), last = Some(Now - 9 * Min)).left.exists(_.contains("less than stale-minutes"))
    )
    assertEquals(decide(Some(RefreshNeed.StaleOnly), last = Some(Now - 10 * Min)), Right(Some(RefreshNeed.StaleOnly)))
    assert(throttled(Some(Now - Min), 10 * Min, Now) && !throttled(None, 10 * Min, Now))
    assertEquals(decide(None, last = Some(Now - Min), force = true), Right(None), "forced: no need, no throttle")
    assert(decide(None, login = Login.Missing("not logged in"), force = true).left.exists(_.startsWith("no login")))

  test("#989: no login means no refresh; an unknown login does not stop one; refresh off is off"):
    assert(decide(Some(RefreshNeed.NoReading), login = Login.Missing("x")).left.exists(_.contains("no login: x")))
    assertEquals(
      decide(Some(RefreshNeed.NoReading), login = Login.Unknown("timeout")),
      Right(Some(RefreshNeed.NoReading))
    )
    assert(decide(Some(RefreshNeed.StaleOnly), enabled = false).left.exists(_.contains("off")))
    assertEquals(RefreshMode.parse(" Probe "), Some(RefreshMode.Probe))
    assertEquals(RefreshMode.parse("sometimes"), None)
    assertEquals(Source.parse("probe"), Some(Source.Probe))
