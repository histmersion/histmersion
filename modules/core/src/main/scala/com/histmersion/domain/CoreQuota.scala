package com.histmersion
package domain

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.library.UpstreamQuota

import java.time.{Instant, LocalDate, OffsetDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import scala.util.Try
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The budget Histmersion keeps for CORE's API (api.core.ac.uk), shared by its two uses: the `core` step of the
  * acquisition chain (a PDF lookup by DOI) and the `core` index of the new-source search (a goal's saved query). Pure:
  * the state goes in, the next state comes out; the server holds it.
  *
  * CORE meters a key in tokens — a personal key 1,000 a day and 25 a minute, a simple query 1 token, a scroll,
  * recommender or bulk call 3–5 — and says so on every answer: `X-RateLimit-Limit` and `X-RateLimit-Remaining` for the
  * current window, `X-RateLimit-Retry-After` (an ISO time, e.g. `2026-10-26T18:52:11+0000`) once the window is spent,
  * and HTTP 429 when a call came too soon. CORE may change the costs and the limits, so all of them are settings.
  *
  *   - **Blocked**: after a 429, or `Remaining` 0, nothing is sent until the time CORE gave (or, without one,
  *     `exhaustedPauseSeconds` later).
  *   - **Pacing**: at most `perMinute` calls in any 60 s; a call that would wait longer than `maxWaitSeconds` for its
  *     slot is not made.
  *   - **Daily budget**: at most `dailyTokens` a day (a UTC day: CORE does not say when its day starts), of which
  *     searches may use `searchDailyTokens` — the rest stays for PDF lookups, so a sweep of many saved queries cannot
  *     leave the ingests without CORE.
  */
object CoreQuota:
  type Cap = Cap.T
  object Cap extends RefinedType[Int, Pure]
  type Cost = Cost.T
  object Cost extends RefinedType[Int, Pure]
  type Description = Description.T
  object Description extends RefinedType[String, Pure]
  type HeaderName = HeaderName.T
  object HeaderName extends RefinedType[String, Pure]
  type HeaderValue = HeaderValue.T
  object HeaderValue extends RefinedType[String, Pure]
  type Spent = Spent.T
  object Spent extends RefinedType[Int, Pure]
  type StatusCode = StatusCode.T
  object StatusCode extends RefinedType[Int, Pure]

  /** What a call to CORE is for: the share of the daily budget it spends. */
  enum Purpose(val wire: Purpose.Wire, val label: Purpose.Label):
    /** The acquisition chain's `core` step: a work's download URLs by its DOI. */
    case Lookup extends Purpose(Purpose.Wire("lookup"), Purpose.Label("PDF lookups"))

    /** The new-source search's `core` index: one saved query of a goal. */
    case Search extends Purpose(Purpose.Wire("search"), Purpose.Label("searches"))

  object Purpose:
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]
    type Label = Label.T
    object Label extends RefinedType[String, Pure]

  /** The limits Histmersion keeps to (`acquire.core.*`). */
  final case class Limits(
      perMinute: Limits.PerMinute,
      dailyTokens: Limits.DailyTokens,
      searchDailyTokens: Limits.SearchDailyTokens,
      lookupCost: Limits.LookupCost,
      searchCost: Limits.SearchCost,
      exhaustedPauseSeconds: Limits.ExhaustedPauseSeconds,
      maxWaitSeconds: Limits.MaxWaitSeconds
  ):
    def cost(p: Purpose): Cost = p match
      case Purpose.Lookup => Cost(lookupCost.value.max(0))
      case Purpose.Search => Cost(searchCost.value.max(0))

    def dailyCap: Cap = Cap(dailyTokens.value)
    def searchCap: Cap = Cap(searchDailyTokens.value)

  object Limits:

    type PerMinute = PerMinute.T
    object PerMinute extends RefinedType[Int, Pure]
    type DailyTokens = DailyTokens.T
    object DailyTokens extends RefinedType[Int, Pure]
    type SearchDailyTokens = SearchDailyTokens.T
    object SearchDailyTokens extends RefinedType[Int, Pure]
    type LookupCost = LookupCost.T
    object LookupCost extends RefinedType[Int, Pure]
    type SearchCost = SearchCost.T
    object SearchCost extends RefinedType[Int, Pure]
    type ExhaustedPauseSeconds = ExhaustedPauseSeconds.T
    object ExhaustedPauseSeconds extends RefinedType[Int, Pure]
    type MaxWaitSeconds = MaxWaitSeconds.T
    object MaxWaitSeconds extends RefinedType[Int, Pure]

  /** CORE asked not to be called until `until`; `why` says what it answered. */
  final case class Block(until: Instant, why: Block.Why)

  object Block:
    type Why = Why.T
    object Why extends RefinedType[String, Pure]

  /** What the last answer's rate-limit headers said. */
  final case class Window(limit: Option[Window.Limit], remaining: Option[Window.Remaining], at: Instant)

  object Window:
    type Limit = Limit.T
    object Limit extends RefinedType[Int, Pure]
    type Remaining = Remaining.T
    object Remaining extends RefinedType[Int, Pure]

  /** The budget's state. `recent` — the slots of the calls of the last minute — lives in memory only: a restart forgets
    * at most one minute of pacing, which CORE's own 429 then covers. The rest is kept in the database, so a restart
    * does not hand out the day's tokens again or call CORE while it said to wait.
    */
  final case class State(
      day: LocalDate,
      lookupTokens: State.LookupTokens,
      searchTokens: State.SearchTokens,
      block: Option[Block],
      window: Option[Window],
      recent: Vector[Instant] = Vector.empty
  ):
    def spent: Spent = Spent(lookupTokens.value + searchTokens.value)
    def spentOn(p: Purpose): Spent = p match
      case Purpose.Lookup => Spent(lookupTokens.value)
      case Purpose.Search => Spent(searchTokens.value)

  object State:

    type LookupTokens = LookupTokens.T
    object LookupTokens extends RefinedType[Int, Pure]
    type SearchTokens = SearchTokens.T
    object SearchTokens extends RefinedType[Int, Pure]

    def fresh(day: LocalDate): State = State(day, LookupTokens(0), SearchTokens(0), None, None)

  /** Why a call is not made. */
  enum Refusal:
    case Blocked(until: Instant, why: Refusal.Blocked.Why)
    case OverBudget(purpose: Purpose, spent: Refusal.OverBudget.Spent, cap: Refusal.OverBudget.Cap)
    case Paced(waitMs: Refusal.Paced.WaitMs)

    def describe: Description = this match
      case Blocked(until, why)       => Description(s"CORE asked to wait until $until ($why)")
      case OverBudget(p, spent, cap) =>
        Description(
          s"CORE's daily budget for ${p.label} is spent ($spent of $cap tokens today, UTC; acquire.core." +
            s"${if p == Purpose.Search then "search-daily-tokens" else "daily-tokens"})"
        )
      case Paced(waitMs) =>
        Description(
          s"CORE's next free slot is ${(waitMs.value + 999) / 1000} s away (acquire.core.per-minute), longer than " +
            "acquire.core.max-wait-seconds"
        )

  object Refusal:
    object Blocked:
      export Block.Why

    object OverBudget:
      export CoreQuota.{Spent, Cap}

    object Paced:
      export Grant.WaitMs


  /** A call may go out after `waitMs`; `state` already counts it. */
  final case class Grant(state: State, waitMs: Grant.WaitMs)

  object Grant:
    type WaitMs = WaitMs.T
    object WaitMs extends RefinedType[Long, Pure]

  private val Minute = 60_000L

  /** The UTC day of `now`. */
  def day(now: Instant): LocalDate = now.atZone(ZoneOffset.UTC).toLocalDate

  /** `s` on `now`'s day: a new day starts with no tokens spent; a block and the recent slots stay. */
  def onDay(s: State, now: Instant): State =
    val today = day(now)
    if s.day == today then s else s.modify(_.day).setTo(today).modify(_.lookupTokens).setTo(State.LookupTokens(0)).modify(_.searchTokens).setTo(State.SearchTokens(0))

  /** May a call for `purpose` go out at `now`? Counts it when it may (the reservation is made before the call is sent,
    * so callers on several threads never overspend together).
    */
  def reserve(s0: State, limits: Limits, purpose: Purpose, now: Instant): Either[Refusal, Grant] =
    val s = onDay(s0, now)
    val cost = limits.cost(purpose)
    s.block.filter(_.until.isAfter(now)) match
      case Some(b) => Left(Refusal.Blocked(b.until, b.why))
      case None    =>
        if s.spent.value + cost.value > limits.dailyTokens.value then Left(Refusal.OverBudget(purpose, s.spent, limits.dailyCap))
        else if purpose == Purpose.Search && s.searchTokens.value + cost.value > limits.searchDailyTokens.value then
          Left(Refusal.OverBudget(purpose, s.spentOn(Purpose.Search), limits.searchCap))
        else
          // CORE's own reported window wins when it is smaller than ours (a new key may get less than its tier)
          val perMinute = s.window.flatMap(_.limit).map(l => (l.value: Int)).filter(_ > 0).fold(limits.perMinute.value: Int)(_ min limits.perMinute.value).max(1)
          val inWindow = s.recent.filter(t => t.toEpochMilli > now.toEpochMilli - Minute).sortBy(_.toEpochMilli)
          val slot =
            if inWindow.size < perMinute then now
            else Instant.ofEpochMilli((inWindow(inWindow.size - perMinute).toEpochMilli + Minute).max(now.toEpochMilli))
          val waitMs = slot.toEpochMilli - now.toEpochMilli
          if waitMs > limits.maxWaitSeconds.value.toLong.max(0L) * 1000L then Left(Refusal.Paced(Grant.WaitMs(waitMs)))
          else
            val spent = purpose match
              case Purpose.Lookup => s.modify(_.lookupTokens).using(t => State.LookupTokens(t.value + cost.value))
              case Purpose.Search => s.modify(_.searchTokens).using(t => State.SearchTokens(t.value + cost.value))
            Right(
              Grant(
                spent.modify(_.block).setTo(None).modify(_.recent).setTo((inWindow :+ slot).sortBy(_.toEpochMilli).takeRight(perMinute)),
                Grant.WaitMs(waitMs)
              )
            )

  /** The state after CORE answered `code` with these headers (names in lower case) at `now`: the window it reported,
    * and a block after a 429 or when `Remaining` reached 0 — until the time CORE gave, else `exhaustedPauseSeconds`
    * from now. A block is only ever lengthened by an answer.
    */
  def observe(s: State, limits: Limits, code: StatusCode, headers: Map[HeaderName, HeaderValue], now: Instant): State =
    def int(name: HeaderName) = headers.get(name).flatMap(_.value.trim.toIntOption)
    val window = Window(int(HeaderName("x-ratelimit-limit")).map(Window.Limit(_)), int(HeaderName("x-ratelimit-remaining")).map(Window.Remaining(_)), now)
    val retry = retryAt(headers.get(HeaderName("x-ratelimit-retry-after")), now).orElse(retryAt(headers.get(HeaderName("retry-after")), now))
    val pause = now.plusSeconds(limits.exhaustedPauseSeconds.value.toLong.max(1L))
    val until = retry.filter(_.isAfter(now)).getOrElse(pause)
    val why =
      if code.value == 429 then Some(Block.Why("HTTP 429"))
      else if window.remaining.exists(_.value == 0) then
        Some(Block.Why(s"X-RateLimit-Remaining 0 of ${window.limit.fold("?")(_.toString)}"))
      else None
    val block = why.map(Block(until, _)) match
      case Some(b) => Some(s.block.filter(_.until.isAfter(b.until)).getOrElse(b))
      case None    => s.block
    val known = window.limit.isDefined || window.remaining.isDefined
    s.modify(_.block).setTo(block).modify(_.window).setTo(if known then Some(window) else s.window)

  private val CoreTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

  /** A retry time as CORE gives it (`2026-10-26T18:52:11+0000`), an ISO-8601 instant or offset time, delay seconds, or
    * an HTTP date.
    */
  def retryAt(v: Option[HeaderValue], now: Instant): Option[Instant] =
    v.map(_.value.trim).filter(_.nonEmpty).flatMap { s =>
      Try(OffsetDateTime.parse(s, CoreTime).toInstant).toOption
        .orElse(Try(OffsetDateTime.parse(s).toInstant).toOption)
        .orElse(Try(Instant.parse(s)).toOption)
        .orElse(HttpPolicy.retryAfterMs(Some(HttpPolicy.RetryAfter(s)), now).map(ms => now.plusMillis(ms.value)))
    }

  /** The state as the surfaces show it (`histmersion about`, the autopilot status), as of `now`. */
  def view(s0: State, limits: Limits, now: Instant): UpstreamQuota =
    val s = onDay(s0, now)
    val b = s.block.filter(_.until.isAfter(now))
    UpstreamQuota(
      UpstreamQuota.Upstream("core"),
      UpstreamQuota.Day(s.day.toString),
      UpstreamQuota.LookupTokens(s.lookupTokens.value),
      UpstreamQuota.SearchTokens(s.searchTokens.value),
      UpstreamQuota.DailyTokens(limits.dailyTokens.value),
      UpstreamQuota.SearchDailyTokens(limits.searchDailyTokens.value),
      UpstreamQuota.PerMinute(limits.perMinute.value),
      b.map(x => UpstreamQuota.BlockedUntil(x.until.toString)),
      b.map(x => UpstreamQuota.BlockedWhy(x.why.value)),
      s.window.flatMap(_.limit).map(l => UpstreamQuota.RateLimit(l.value)),
      s.window.flatMap(_.remaining).map(m => UpstreamQuota.RateRemaining(m.value)),
      s.window.map(w => UpstreamQuota.ObservedAt(w.at.toString))
    )
