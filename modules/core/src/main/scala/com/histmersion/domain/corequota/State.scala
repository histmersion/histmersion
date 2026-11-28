package com.histmersion
package domain.corequota

import hearth.kindlings.optics.syntax.*
import java.time.Instant
import java.time.LocalDate
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.domain.corequota.Backoff
import com.histmersion.domain.CoreQuota.Spent
import com.histmersion.domain.corequota.Purpose
import com.histmersion.domain.corequota.Window
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Newtype
import com.histmersion.domain.CoreQuota.day
import com.histmersion.domain.corequota.Grant
import com.histmersion.domain.corequota.Denial
import com.histmersion.domain.corequota.Ceilings
import com.histmersion.domain.CoreQuota.HeaderValue
import com.histmersion.domain.CoreQuota.HeaderName
import com.histmersion.domain.CoreQuota.retryAt
import com.histmersion.domain.CoreQuota.StatusCode
import com.histmersion.api.library.UpstreamQuota
import com.histmersion.util.time.TimeEquality.dayEquality

final case class State(
    day: LocalDate,
    lookupTokens: State.LookupTokens,
    searchTokens: State.SearchTokens,
    block: Option[Backoff],
    window: Option[Window],
    recent: Vector[Instant] = Vector.empty
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def spent: Spent = Spent(lookupTokens.value + searchTokens.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def spentOn(p: Purpose): Spent = p match
    case Purpose.Lookup => Spent(lookupTokens.value)
    case Purpose.Search => Spent(searchTokens.value)

object State:

  type LookupTokens = LookupTokens.T
  object LookupTokens extends Newtype[Int, Pure]
  type SearchTokens = SearchTokens.T
  object SearchTokens extends Newtype[Int, Pure]

  def fresh(day: LocalDate): State = State(day, LookupTokens(0), SearchTokens(0), None, None)

  private val Minute = 60_000L

  extension (s: State)
    def onDay(now: Instant): State =
      val today = day(now)
      if s.day == today then s
      else
        s.modify(_.day)
          .setTo(today)
          .modify(_.lookupTokens)
          .setTo(LookupTokens(0))
          .modify(_.searchTokens)
          .setTo(SearchTokens(0))

  extension (s0: State)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def reserve(limits: Ceilings, purpose: Purpose, now: Instant): Either[Denial, Grant] =
      val s = s0.onDay(now)
      val cost = limits.cost(purpose)
      s.block.filter(_.until.isAfter(now)) match
        case Some(b) => Left(Denial.Blocked(b.until, b.why))
        case None    =>
          if s.spent.value + cost.value > limits.dailyTokens.value then
            Left(Denial.OverBudget(purpose, s.spent, limits.dailyCap))
          else if purpose == Purpose.Search && s.searchTokens.value + cost.value > limits.searchDailyTokens.value then
            Left(Denial.OverBudget(purpose, s.spentOn(Purpose.Search), limits.searchCap))
          else
            val perMinute = s.window
              .flatMap(_.limit)
              .map(l => (l.value: Int))
              .filter(_ > 0)
              .fold(limits.perMinute.value: Int)(_ min limits.perMinute.value)
              .max(1)
            val inWindow = s.recent.filter(t => t.toEpochMilli > now.toEpochMilli - Minute).sortBy(_.toEpochMilli)
            val slot =
              if inWindow.size < perMinute then now
              else
                Instant.ofEpochMilli((inWindow(inWindow.size - perMinute).toEpochMilli + Minute).max(now.toEpochMilli))
            val waitMs = slot.toEpochMilli - now.toEpochMilli
            if waitMs > limits.maxWaitSeconds.value.toLong.max(0L) * 1000L then Left(Denial.Paced(Grant.WaitMs(waitMs)))
            else
              val spent = purpose match
                case Purpose.Lookup => s.modify(_.lookupTokens).using(t => LookupTokens(t.value + cost.value))
                case Purpose.Search => s.modify(_.searchTokens).using(t => SearchTokens(t.value + cost.value))
              Right(
                Grant(
                  spent
                    .modify(_.block)
                    .setTo(None)
                    .modify(_.recent)
                    .setTo((inWindow :+ slot).sortBy(_.toEpochMilli).takeRight(perMinute)),
                  Grant.WaitMs(waitMs)
                )
              )

  extension (s: State)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def observe(limits: Ceilings, code: StatusCode, headers: Map[HeaderName, HeaderValue], now: Instant): State =
      def int(name: HeaderName) = headers.get(name).flatMap(_.value.trim.toIntOption)
      val window = Window(
        int(HeaderName("x-ratelimit-limit")).map(Window.Limit(_)),
        int(HeaderName("x-ratelimit-remaining")).map(Window.Remaining(_)),
        now
      )
      val retry = retryAt(headers.get(HeaderName("x-ratelimit-retry-after")), now)
        .orElse(retryAt(headers.get(HeaderName("retry-after")), now))
      val pause = now.plusSeconds(limits.exhaustedPauseSeconds.value.toLong.max(1L))
      val until = retry.filter(_.isAfter(now)).getOrElse(pause)
      val why =
        if code.value == 429 then Some(Backoff.Rationale.Throttled)
        else if window.remaining.exists(_.value == 0) then Some(Backoff.Rationale.Exhausted(window.limit))
        else None
      val block = why.map(Backoff(until, _)) match
        case Some(b) => Some(s.block.filter(_.until.isAfter(b.until)).getOrElse(b))
        case None    => s.block
      val known = window.limit.isDefined || window.remaining.isDefined
      s.modify(_.block).setTo(block).modify(_.window).setTo(if known then Some(window) else s.window)

  extension (s0: State)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def view(limits: Ceilings, now: Instant): UpstreamQuota =
      val s = s0.onDay(now)
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
        b.map(x => UpstreamQuota.BlockedBy(x.why.said)),
        s.window.flatMap(_.limit).map(l => UpstreamQuota.RateLimit(l.value)),
        s.window.flatMap(_.remaining).map(m => UpstreamQuota.RateRemaining(m.value)),
        s.window.map(w => UpstreamQuota.ObservedAt(w.at.toString))
      )
