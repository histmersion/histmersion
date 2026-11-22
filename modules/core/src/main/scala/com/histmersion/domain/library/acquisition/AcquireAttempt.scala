package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.AcquireRecheck
import com.histmersion.domain.library.Citekey
import com.histmersion.util.problem.{Problem, Problems}
import AcquireAttempt.{looked, unanswered, Oa, Outcome, Trigger}

import java.time.Instant
import io.github.iltotore.iron.constraint.any.Pure

final case class AcquireAttempt(
    at: Instant,
    trigger: Trigger,
    generation: AcquireRecheck.LastCheck.Generation,
    outcome: Outcome,
    answers: Vector[Problem.NotFound.Answer],
    oa: Option[Oa]
):
  def failed: Boolean = outcome match
    case Outcome.Failed(_) => true
    case Outcome.NoCopy    => answers.exists(unanswered) || !answers.exists(looked)
    case _                 => false

  def refused(refusal: Problem.NotFound.Refusal): Boolean =
    answers.exists(_.said == Problem.NotFound.Said.Refused(refusal))

  def lastProblem: Option[Problems] = outcome match
    case Outcome.Failed(why) => Some(why)
    case _                   => Problems.from(answers.filter(unanswered).map(_.why))

  def scriptsRefused: Boolean = oa.exists(_.scripts == Oa.Scripts.Refused)

  def openLocation: Boolean = oa.exists(_.url.isDefined)

object AcquireAttempt:
  type Tries = Tries.T
  object Tries extends Newtype[Int, Pure]:
    val One: Tries = Tries(1)

    def of(attempts: List[AcquireAttempt]): Tries =
      Tries(
        attempts
          .map(_.outcome match
            case Outcome.Legacy(_, checks) => checks
            case _                         => One)
          .map(t => math.max(1, t.value))
          .sum
      )

  type Kept = Kept.T
  object Kept extends Newtype[Int, Pure]:
    extension (k: Kept) def keep[A](newestFirst: List[A]): List[A] = newestFirst.take(math.max(1, k.value))

  enum Trigger derives CanEqual:
    case PersonIngest, WatcherBacklog, Recheck, Legacy

  import Outcome.{LegacyNote, Source}

  enum Outcome derives CanEqual:
    case Closed(citekey: Citekey)
    case Fetched(source: Source)
    case NoCopy
    case Failed(why: Problems)
    case Legacy(note: LegacyNote, checks: Tries)

  object Outcome:
    type Source = Source.T
    object Source extends Newtype[String, Pure]
    type LegacyNote = LegacyNote.T
    object LegacyNote extends Newtype[String, Pure]

  import Oa.{OaLicence, OaStatus, OaUrl, Scripts}

  final case class Oa(status: Option[OaStatus], url: Option[OaUrl], licence: Option[OaLicence], scripts: Scripts)

  object Oa:
    type OaStatus = OaStatus.T
    object OaStatus extends Newtype[String, Pure]
    type OaUrl = OaUrl.T
    object OaUrl extends Newtype[String, Pure]
    type OaLicence = OaLicence.T
    object OaLicence extends Newtype[String, Pure]

    enum Scripts derives CanEqual:
      case Allowed, Refused

  def unanswered(answer: Problem.NotFound.Answer): Boolean = answer.said match
    case Problem.NotFound.Said.Miss(Problem.NotFound.Absence.Unanswered)  => true
    case Problem.NotFound.Said.Miss(Problem.NotFound.Absence.RateLimited) => true
    case _                                                                => false

  def looked(answer: Problem.NotFound.Answer): Boolean = answer.said match
    case Problem.NotFound.Said.Miss(Problem.NotFound.Absence.NotHeld) => true
    case _: Problem.NotFound.Said.Refused                             => true
    case _                                                            => false
