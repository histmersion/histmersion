package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.acquirerecheck.LastCheck

import com.histmersion.domain.{AcquireRecheck, Actor}
import com.histmersion.util.problem.{Problem, Problems}

import java.time.Instant
import scala.annotation.tailrec
import io.github.iltotore.iron.constraint.any.Pure
import AcquireVerdict.Failures

enum AcquireVerdict derives CanEqual:
  case Unchecked(due: Instant)
  case Converting(source: Option[AcquireAttempt.Outcome.Source])
  case Retrying(failures: Failures, next: Instant, last: Option[Problems])
  case Stuck(failures: Failures, since: Instant, last: Option[Problems])
  case ManualDownload(url: Option[AcquireAttempt.Oa.OaUrl])
  case Borrow
  case NoFreeCopy(recheck: Instant)
  case NoIdentifier

  def actor: Actor = this match
    case _: Unchecked | _: Converting | _: Retrying                           => Actor.Agents
    case _: Stuck | _: ManualDownload | Borrow | _: NoFreeCopy | NoIdentifier => Actor.Person

object AcquireVerdict:
  type Failures = Failures.T
  object Failures extends Newtype[Int, Pure]:
    extension (f: Failures) def reaches(limit: Failures): Boolean = f.value >= math.max(1, limit.value)

    def streak(newestFirst: List[AcquireAttempt]): (Failures, Option[Instant]) =
      @tailrec
      def loop(rest: List[AcquireAttempt], n: Int, since: Option[Instant]): (Failures, Option[Instant]) = rest match
        case a :: tail if a.failed => loop(tail, n + 1, Some(a.at))
        case _                     => (Failures(n), since)
      loop(newestFirst, 0, None)

  enum Identifier derives CanEqual:
    case Named, Missing

  import Settings.Days

  final case class Settings(
      everyDays: domain.acquirerecheck.Cadence.EveryDays,
      retryHours: domain.acquirerecheck.Cadence.RetryHours,
      giveUpChecks: Failures,
      giveUpDays: Days
  )

  object Settings:
    type Days = Days.T
    object Days extends Newtype[Int, Pure]:
      extension (g: Days)
        def passedSince(since: Instant, now: Instant): Boolean =
          !since.plusSeconds(math.max(1, g.value).toLong * 86400L).isAfter(now)

  def of(
      identifier: Identifier,
      newestFirst: List[AcquireAttempt],
      generation: LastCheck.Generation,
      settings: Settings,
      now: Instant
  ): AcquireVerdict =
    identifier match
      case Identifier.Missing => NoIdentifier
      case Identifier.Named   =>
        newestFirst.filter(_.generation == generation) match
          case Nil            => Unchecked(now)
          case last :: before => ofLast(last, last :: before, settings, now)

  private def ofLast(
      last: AcquireAttempt,
      current: List[AcquireAttempt],
      settings: Settings,
      now: Instant
  ): AcquireVerdict =
    last.outcome match
      case AcquireAttempt.Outcome.Closed(_)                                 => Converting(None)
      case AcquireAttempt.Outcome.Fetched(source)                           => Converting(Some(source))
      case AcquireAttempt.Outcome.Legacy(_, _)                              => Unchecked(now)
      case AcquireAttempt.Outcome.NoCopy | AcquireAttempt.Outcome.Failed(_) =>
        if last.refused(Problem.NotFound.Refusal.BotChallenge) || last.scriptsRefused then download(last)
        else if last.refused(Problem.NotFound.Refusal.LendingOnly) then Borrow
        else if last.failed then failing(last, current, settings, now)
        else if last.openLocation then download(last)
        else NoFreeCopy(settings.everyDays.after(last.at))

  private def download(last: AcquireAttempt): AcquireVerdict =
    ManualDownload(last.oa.flatMap(_.url))

  private def failing(
      last: AcquireAttempt,
      current: List[AcquireAttempt],
      settings: Settings,
      now: Instant
  ): AcquireVerdict =
    val (failures, first) = Failures.streak(current)
    val since = first.getOrElse(last.at)
    if failures.reaches(settings.giveUpChecks) || settings.giveUpDays.passedSince(since, now) then
      Stuck(failures, since, last.lastProblem)
    else Retrying(failures, settings.retryHours.after(last.at), last.lastProblem)
