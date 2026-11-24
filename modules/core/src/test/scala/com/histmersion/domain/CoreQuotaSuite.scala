package com.histmersion
package domain

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.CoreQuota.*

import java.time.{Instant, LocalDate}
import com.histmersion.lint.{LintRule, tolerated}

/** CORE's budget, pure: CORE's blocks (429, `Remaining` 0, its retry time), the per-minute pace, the daily budget and
  * the searches' share of it, and the day's rollover.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class CoreQuotaSuite extends munit.FunSuite:

  private val limits = Limits(
    perMinute = CoreQuota.Limits.PerMinute(3),
    dailyTokens = CoreQuota.Limits.DailyTokens(10),
    searchDailyTokens = CoreQuota.Limits.SearchDailyTokens(6),
    lookupCost = CoreQuota.Limits.LookupCost(1),
    searchCost = CoreQuota.Limits.SearchCost(1),
    exhaustedPauseSeconds = CoreQuota.Limits.ExhaustedPauseSeconds(60),
    maxWaitSeconds = CoreQuota.Limits.MaxWaitSeconds(0)
  )
  private val t0 = Instant.parse("2026-10-26T18:50:00Z")
  private val fresh = State.fresh(LocalDate.of(2026, 10, 26))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def granted(s: State, p: Purpose = Purpose.Lookup, at: Instant = t0, l: Limits = limits): State =
    reserve(s, l, p, at).fold(r => fail(s"refused: ${r.describe}"), _.state)

  test("CORE's retry time is read as CORE writes it, and as ISO, seconds or an HTTP date"):
    assertEquals(
      retryAt(Some(CoreQuota.HeaderValue("2026-10-26T18:52:11+0000")), t0),
      Some(Instant.parse("2026-10-26T18:52:11Z"))
    )
    assertEquals(
      retryAt(Some(CoreQuota.HeaderValue("2026-10-26T20:52:11+02:00")), t0),
      Some(Instant.parse("2026-10-26T18:52:11Z"))
    )
    assertEquals(
      retryAt(Some(CoreQuota.HeaderValue("2026-10-26T18:52:11Z")), t0),
      Some(Instant.parse("2026-10-26T18:52:11Z"))
    )
    assertEquals(retryAt(Some(CoreQuota.HeaderValue("90")), t0), Some(t0.plusSeconds(90)))
    assertEquals(
      retryAt(Some(CoreQuota.HeaderValue("Mon, 26 Oct 2026 18:52:11 GMT")), t0),
      Some(Instant.parse("2026-10-26T18:52:11Z"))
    )
    assertEquals(retryAt(Some(CoreQuota.HeaderValue("soon")), t0), None)
    assertEquals(retryAt(None, t0), None)

  test("a 429 with X-RateLimit-Retry-After blocks every use until that time, then CORE is asked again"):
    val s = observe(
      granted(fresh),
      limits,
      CoreQuota.StatusCode(429),
      Map(
        HeaderName("x-ratelimit-limit") -> HeaderValue("25"),
        HeaderName("x-ratelimit-remaining") -> HeaderValue("0"),
        HeaderName("x-ratelimit-retry-after") -> HeaderValue("2026-10-26T18:52:11+0000")
      ),
      t0
    )
    val until = Instant.parse("2026-10-26T18:52:11Z")
    assertEquals(s.block, Some(Block(until, CoreQuota.Block.Rationale.Throttled)))
    assertEquals(s.window.map(w => (w.limit, w.remaining)), Some((Some(25), Some(0))))
    Purpose.values.foreach { p =>
      assertEquals(
        reserve(s, limits, p, t0.plusSeconds(60)).left.toOption,
        Some(Refusal.Blocked(until, CoreQuota.Block.Rationale.Throttled))
      )
    }
    val after = reserve(s, limits, Purpose.Lookup, until.plusSeconds(1))
    assert(after.isRight, after.toString)
    assertEquals(after.toOption.get.state.block, None)

  test("Remaining 0 without a 429 blocks too; without a retry time, for exhausted-pause-seconds"):
    val s = observe(
      fresh,
      limits,
      CoreQuota.StatusCode(200),
      Map(
        HeaderName("x-ratelimit-limit") -> HeaderValue("10"),
        HeaderName("x-ratelimit-remaining") -> HeaderValue("0")
      ),
      t0
    )
    assertEquals(
      s.block,
      Some(Block(t0.plusSeconds(60), CoreQuota.Block.Rationale.Exhausted(Some(CoreQuota.Window.Limit(10)))))
    )
    val plain429 = observe(fresh, limits, CoreQuota.StatusCode(429), Map.empty, t0)
    assertEquals(plain429.block.map(_.until), Some(t0.plusSeconds(60)))
    // the standard Retry-After header counts when CORE's own is missing
    assertEquals(
      observe(fresh, limits, CoreQuota.StatusCode(429), Map(HeaderName("retry-after") -> HeaderValue("120")), t0).block
        .map(_.until),
      Some(t0.plusSeconds(120))
    )

  test("an answer with tokens left blocks nothing and records the window; a later answer never shortens a block"):
    val ok = observe(
      fresh,
      limits,
      CoreQuota.StatusCode(200),
      Map(
        HeaderName("x-ratelimit-limit") -> HeaderValue("25"),
        HeaderName("x-ratelimit-remaining") -> HeaderValue("18")
      ),
      t0
    )
    assertEquals(ok.block, None)
    assertEquals(ok.window, Some(Window(Some(CoreQuota.Window.Limit(25)), Some(CoreQuota.Window.Remaining(18)), t0)))
    val long = observe(
      fresh,
      limits,
      CoreQuota.StatusCode(429),
      Map(HeaderName("x-ratelimit-retry-after") -> HeaderValue("2026-10-26T20:00:00+0000")),
      t0
    )
    val shorter = observe(
      long,
      limits,
      CoreQuota.StatusCode(429),
      Map(HeaderName("x-ratelimit-retry-after") -> HeaderValue("2026-10-26T18:51:00+0000")),
      t0
    )
    assertEquals(shorter.block.map(_.until), Some(Instant.parse("2026-10-26T20:00:00Z")))
    // an answer without the headers keeps the window last reported
    assertEquals(observe(ok, limits, CoreQuota.StatusCode(200), Map.empty, t0.plusSeconds(5)).window, ok.window)

  test(
    "pacing: at most per-minute calls in any 60 s — a call that would wait longer than max-wait-seconds is not made"
  ):
    val three = (1 to 3).foldLeft(fresh)((s, i) => granted(s, at = t0.plusSeconds(i.toLong)))
    reserve(three, limits, Purpose.Lookup, t0.plusSeconds(10)) match
      case Left(Refusal.Paced(ms)) => assertEquals(ms, 51_000L) // the first slot (t0+1 s) leaves the window at t0+61 s
      case other                   => fail(other.toString)
    // with patience, the call waits for its slot (counted now, sent then)
    val patient = limits.modify(_.maxWaitSeconds).setTo(Limits.MaxWaitSeconds(60))
    val g = reserve(three, patient, Purpose.Search, t0.plusSeconds(10)).toOption.get
    assertEquals(g.waitMs, 51_000L)
    assertEquals(g.state.searchTokens, 1)
    // a minute later the window is free again
    assert(reserve(three, limits, Purpose.Lookup, t0.plusSeconds(62)).isRight)

  test("pacing follows CORE's reported window when it is smaller than per-minute"):
    val roomy = limits.modify(_.perMinute).setTo(Limits.PerMinute(20))
    val window = observe(
      fresh,
      roomy,
      CoreQuota.StatusCode(200),
      Map(HeaderName("x-ratelimit-limit") -> HeaderValue("2"), HeaderName("x-ratelimit-remaining") -> HeaderValue("2")),
      t0
    )
    val two = (1 to 2).foldLeft(window)((s, i) => granted(s, at = t0.plusSeconds(i.toLong), l = roomy))
    assert(reserve(two, roomy, Purpose.Lookup, t0.plusSeconds(10)).left.exists(_.isInstanceOf[Refusal.Paced]))
    // a reported window larger than per-minute changes nothing
    val wide = observe(
      fresh,
      limits,
      CoreQuota.StatusCode(200),
      Map(
        HeaderName("x-ratelimit-limit") -> HeaderValue("25"),
        HeaderName("x-ratelimit-remaining") -> HeaderValue("25")
      ),
      t0
    )
    val three = (1 to 3).foldLeft(wide)((s, i) => granted(s, at = t0.plusSeconds(i.toLong)))
    assert(reserve(three, limits, Purpose.Lookup, t0.plusSeconds(10)).isLeft)

  test(
    "the daily budget: searches stop at search-daily-tokens, the rest stays for PDF lookups; together at daily-tokens"
  ):
    val free = limits.modify(_.perMinute).setTo(Limits.PerMinute(1000))
    val searches = (1 to 6).foldLeft(fresh)((s, _) => granted(s, Purpose.Search, l = free))
    assertEquals(
      reserve(searches, free, Purpose.Search, t0).left.toOption,
      Some(
        Refusal.OverBudget(Purpose.Search, CoreQuota.Refusal.OverBudget.Spent(6), CoreQuota.Refusal.OverBudget.Cap(6))
      )
    )
    val lookups = (1 to 4).foldLeft(searches)((s, _) => granted(s, Purpose.Lookup, l = free))
    assertEquals((lookups.lookupTokens, lookups.searchTokens, lookups.spent), (4, 6, 10))
    assertEquals(
      reserve(lookups, free, Purpose.Lookup, t0).left.toOption,
      Some(
        Refusal.OverBudget(Purpose.Lookup, CoreQuota.Refusal.OverBudget.Spent(10), CoreQuota.Refusal.OverBudget.Cap(10))
      )
    )
    // lookups may use what searches left: ten lookups, no searches
    val allLookups = (1 to 10).foldLeft(fresh)((s, _) => granted(s, Purpose.Lookup, l = free))
    assertEquals(allLookups.lookupTokens, 10)
    // and searches cannot eat the lookups' reserve even when lookups have not used it yet
    assert(reserve(searches, free, Purpose.Search, t0).isLeft)

  test("costs are settings: a call costing 3 tokens counts 3"):
    val dear = limits.modify(_.perMinute).setTo(Limits.PerMinute(1000)).modify(_.searchCost).setTo(Limits.SearchCost(3))
    val s = granted(granted(fresh, Purpose.Search, l = dear), Purpose.Search, l = dear)
    assertEquals(s.searchTokens, 6)
    assert(reserve(s, dear, Purpose.Search, t0).isLeft)

  test("a new UTC day starts with nothing spent; a block that has not ended stays"):
    val spent = (1 to 3).foldLeft(fresh)((s, i) => granted(s, at = t0.plusSeconds(i.toLong)))
    val blocked = observe(
      spent,
      limits,
      CoreQuota.StatusCode(429),
      Map(HeaderName("x-ratelimit-retry-after") -> HeaderValue("2026-10-27T00:30:00+0000")),
      t0
    )
    val midnight = Instant.parse("2026-10-27T00:10:00Z")
    val next = onDay(blocked, midnight)
    assertEquals((next.day, next.spent), (LocalDate.of(2026, 10, 27), 0))
    assert(reserve(blocked, limits, Purpose.Lookup, midnight).left.toOption.exists(_.isInstanceOf[Refusal.Blocked]))
    assertEquals(
      reserve(blocked, limits, Purpose.Lookup, Instant.parse("2026-10-27T00:31:00Z")).map(_.state.lookupTokens),
      Right(1)
    )

  test("the view: today's spending under the limits, the block only while it lasts"):
    val s = observe(
      granted(granted(fresh), Purpose.Search, at = t0.plusSeconds(1)),
      limits,
      CoreQuota.StatusCode(429),
      Map(
        HeaderName("x-ratelimit-limit") -> HeaderValue("25"),
        HeaderName("x-ratelimit-remaining") -> HeaderValue("0"),
        HeaderName("x-ratelimit-retry-after") -> HeaderValue("2026-10-26T18:52:11+0000")
      ),
      t0
    )
    val v = view(s, limits, t0)
    assertEquals(
      (v.upstream, v.day, v.lookupTokens, v.searchTokens, v.dailyTokens, v.searchDailyTokens, v.perMinute),
      ("core", "2026-10-26", 1, 1, 10, 6, 3)
    )
    assertEquals(
      (v.blockedUntil, v.blockedWhy, v.rateLimit, v.rateRemaining),
      (Some("2026-10-26T18:52:11Z"), Some("HTTP 429"), Some(25), Some(0))
    )
    assertEquals(view(s, limits, Instant.parse("2026-10-26T19:00:00Z")).blockedUntil, None)
    assertEquals(view(s, limits, Instant.parse("2026-10-27T09:00:00Z")).lookupTokens, 0)

  test("refusals say what to change"):
    assert(
      Refusal
        .OverBudget(Purpose.Search, CoreQuota.Refusal.OverBudget.Spent(600), CoreQuota.Refusal.OverBudget.Cap(600))
        .describe
        .value
        .contains("acquire.core.search-daily-tokens")
    )
    assert(
      Refusal
        .OverBudget(Purpose.Lookup, CoreQuota.Refusal.OverBudget.Spent(900), CoreQuota.Refusal.OverBudget.Cap(900))
        .describe
        .value
        .contains("acquire.core.daily-tokens")
    )
    assert(Refusal.Paced(Refusal.Paced.WaitMs(4_200L)).describe.value.contains("5 s"))
    assert(Refusal.Blocked(t0, CoreQuota.Block.Rationale.Throttled).describe.value.contains("HTTP 429"))
