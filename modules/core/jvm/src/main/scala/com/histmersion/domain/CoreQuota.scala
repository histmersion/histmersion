package com.histmersion.domain

import com.histmersion.api.UpstreamQuota

import java.time.{Instant, LocalDate, OffsetDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import scala.util.Try

/** The budget Histmersion keeps for CORE's API (api.core.ac.uk), shared by its two uses: the `core` step
  * of the acquisition chain (a PDF lookup by DOI) and the `core` index of the new-source search (a
  * goal's saved query). Pure: the state goes in, the next state comes out; the server holds it.
  *
  * CORE meters a key in tokens — a personal key 1,000 a day and 25 a minute, a simple query 1 token,
  * a scroll, recommender or bulk call 3–5 — and says so on every answer: `X-RateLimit-Limit` and
  * `X-RateLimit-Remaining` for the current window, `X-RateLimit-Retry-After` (an ISO time, e.g.
  * `2026-10-26T18:52:11+0000`) once the window is spent, and HTTP 429 when a call came too soon. CORE
  * may change the costs and the limits, so all of them are settings.
  *
  *  - **Blocked**: after a 429, or `Remaining` 0, nothing is sent until the time CORE gave (or, without
  *    one, `exhaustedPauseSeconds` later).
  *  - **Pacing**: at most `perMinute` calls in any 60 s; a call that would wait longer than
  *    `maxWaitSeconds` for its slot is not made.
  *  - **Daily budget**: at most `dailyTokens` a day (a UTC day: CORE does not say when its day starts),
  *    of which searches may use `searchDailyTokens` — the rest stays for PDF lookups, so a sweep of many
  *    saved queries cannot leave the ingests without CORE. */
object CoreQuota:

  /** What a call to CORE is for: the share of the daily budget it spends. */
  enum Purpose(val wire: String, val label: String):
    /** The acquisition chain's `core` step: a work's download URLs by its DOI. */
    case Lookup extends Purpose("lookup", "PDF lookups")
    /** The new-source search's `core` index: one saved query of a goal. */
    case Search extends Purpose("search", "searches")

  /** The limits Histmersion keeps to (`acquire.core.*`). */
  final case class Limits(perMinute: Int, dailyTokens: Int, searchDailyTokens: Int, lookupCost: Int, searchCost: Int,
      exhaustedPauseSeconds: Int, maxWaitSeconds: Int):
    def cost(p: Purpose): Int = p match
      case Purpose.Lookup => lookupCost.max(0)
      case Purpose.Search => searchCost.max(0)

  /** CORE asked not to be called until `until`; `why` says what it answered. */
  final case class Block(until: Instant, why: String)

  /** What the last answer's rate-limit headers said. */
  final case class Window(limit: Option[Int], remaining: Option[Int], at: Instant)

  /** The budget's state. `recent` — the slots of the calls of the last minute — lives in memory only: a
    * restart forgets at most one minute of pacing, which CORE's own 429 then covers. The rest is kept
    * in the database, so a restart does not hand out the day's tokens again or call CORE while it said
    * to wait. */
  final case class State(day: LocalDate, lookupTokens: Int, searchTokens: Int, block: Option[Block],
      window: Option[Window], recent: Vector[Instant] = Vector.empty):
    def spent: Int = lookupTokens + searchTokens
    def spentOn(p: Purpose): Int = p match
      case Purpose.Lookup => lookupTokens
      case Purpose.Search => searchTokens

  object State:
    def fresh(day: LocalDate): State = State(day, 0, 0, None, None)

  /** Why a call is not made. */
  enum Refusal:
    case Blocked(until: Instant, why: String)
    case OverBudget(purpose: Purpose, spent: Int, cap: Int)
    case Paced(waitMs: Long)

    def describe: String = this match
      case Blocked(until, why)          => s"CORE asked to wait until $until ($why)"
      case OverBudget(p, spent, cap)    =>
        s"CORE's daily budget for ${p.label} is spent ($spent of $cap tokens today, UTC; acquire.core." +
          s"${if p == Purpose.Search then "search-daily-tokens" else "daily-tokens"})"
      case Paced(waitMs)                =>
        s"CORE's next free slot is ${(waitMs + 999) / 1000} s away (acquire.core.per-minute), longer than " +
          "acquire.core.max-wait-seconds"

  /** A call may go out after `waitMs`; `state` already counts it. */
  final case class Grant(state: State, waitMs: Long)

  private val Minute = 60_000L

  /** The UTC day of `now`. */
  def day(now: Instant): LocalDate = now.atZone(ZoneOffset.UTC).toLocalDate

  /** `s` on `now`'s day: a new day starts with no tokens spent; a block and the recent slots stay. */
  def onDay(s: State, now: Instant): State =
    val today = day(now)
    if s.day == today then s else s.copy(day = today, lookupTokens = 0, searchTokens = 0)

  /** May a call for `purpose` go out at `now`? Counts it when it may (the reservation is made before the
    * call is sent, so callers on several threads never overspend together). */
  def reserve(s0: State, limits: Limits, purpose: Purpose, now: Instant): Either[Refusal, Grant] =
    val s    = onDay(s0, now)
    val cost = limits.cost(purpose)
    s.block.filter(_.until.isAfter(now)) match
      case Some(b) => Left(Refusal.Blocked(b.until, b.why))
      case None =>
        if s.spent + cost > limits.dailyTokens then
          Left(Refusal.OverBudget(purpose, s.spent, limits.dailyTokens))
        else if purpose == Purpose.Search && s.searchTokens + cost > limits.searchDailyTokens then
          Left(Refusal.OverBudget(purpose, s.searchTokens, limits.searchDailyTokens))
        else
          // CORE's own reported window wins when it is smaller than ours (a new key may get less than its tier)
          val perMinute = s.window.flatMap(_.limit).filter(_ > 0).fold(limits.perMinute)(_ min limits.perMinute).max(1)
          val inWindow  = s.recent.filter(t => t.toEpochMilli > now.toEpochMilli - Minute).sortBy(_.toEpochMilli)
          val slot =
            if inWindow.size < perMinute then now
            else Instant.ofEpochMilli((inWindow(inWindow.size - perMinute).toEpochMilli + Minute).max(now.toEpochMilli))
          val waitMs = slot.toEpochMilli - now.toEpochMilli
          if waitMs > limits.maxWaitSeconds.toLong.max(0L) * 1000L then Left(Refusal.Paced(waitMs))
          else
            val spent = purpose match
              case Purpose.Lookup => s.copy(lookupTokens = s.lookupTokens + cost)
              case Purpose.Search => s.copy(searchTokens = s.searchTokens + cost)
            Right(Grant(spent.copy(block = None, recent = (inWindow :+ slot).sortBy(_.toEpochMilli).takeRight(perMinute)), waitMs))

  /** The state after CORE answered `code` with these headers (names in lower case) at `now`: the window
    * it reported, and a block after a 429 or when `Remaining` reached 0 — until the time CORE gave, else
    * `exhaustedPauseSeconds` from now. A block is only ever lengthened by an answer. */
  def observe(s: State, limits: Limits, code: Int, headers: Map[String, String], now: Instant): State =
    def int(name: String) = headers.get(name).flatMap(_.trim.toIntOption)
    val window    = Window(int("x-ratelimit-limit"), int("x-ratelimit-remaining"), now)
    val retry     = retryAt(headers.get("x-ratelimit-retry-after"), now).orElse(retryAt(headers.get("retry-after"), now))
    val pause     = now.plusSeconds(limits.exhaustedPauseSeconds.toLong.max(1L))
    val until     = retry.filter(_.isAfter(now)).getOrElse(pause)
    val why =
      if code == 429 then Some("HTTP 429")
      else if window.remaining.contains(0) then Some(s"X-RateLimit-Remaining 0 of ${window.limit.fold("?")(_.toString)}")
      else None
    val block = why.map(Block(until, _)) match
      case Some(b) => Some(s.block.filter(_.until.isAfter(b.until)).getOrElse(b))
      case None    => s.block
    val known = window.limit.isDefined || window.remaining.isDefined
    s.copy(block = block, window = if known then Some(window) else s.window)

  private val CoreTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

  /** A retry time as CORE gives it (`2026-10-26T18:52:11+0000`), an ISO-8601 instant or offset time,
    * delay seconds, or an HTTP date. */
  def retryAt(v: Option[String], now: Instant): Option[Instant] =
    v.map(_.trim).filter(_.nonEmpty).flatMap { s =>
      Try(OffsetDateTime.parse(s, CoreTime).toInstant).toOption
        .orElse(Try(OffsetDateTime.parse(s).toInstant).toOption)
        .orElse(Try(Instant.parse(s)).toOption)
        .orElse(HttpPolicy.retryAfterMs(Some(s), now).map(ms => now.plusMillis(ms)))
    }

  /** The state as the surfaces show it (`histmersion about`, the autopilot status), as of `now`. */
  def view(s0: State, limits: Limits, now: Instant): UpstreamQuota =
    val s = onDay(s0, now)
    val b = s.block.filter(_.until.isAfter(now))
    UpstreamQuota("core", s.day.toString, s.lookupTokens, s.searchTokens, limits.dailyTokens, limits.searchDailyTokens,
      limits.perMinute, b.map(_.until.toString), b.map(_.why), s.window.flatMap(_.limit), s.window.flatMap(_.remaining),
      s.window.map(_.at.toString))
