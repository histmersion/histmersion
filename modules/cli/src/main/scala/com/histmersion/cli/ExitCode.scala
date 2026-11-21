package com.histmersion
package cli

import io.github.iltotore.iron.constraint.any.Pure

import util.problem.{Problem, Problems}
import util.problem.Problem.*

type ExitCode = ExitCode.T

object ExitCode extends Newtype[Int, Pure]:

  val Usage: ExitCode = ExitCode(1)

  val Refused: ExitCode = ExitCode(2)

  val Unreachable: ExitCode = ExitCode(3)

  val NotFound: ExitCode = ExitCode(4)

  val Conflict: ExitCode = ExitCode(5)

  val Unavailable: ExitCode = ExitCode(6)

  val Fault: ExitCode = ExitCode(7)

  val OutcomeUnknown: ExitCode = ExitCode(8)

  val documented: List[(ExitCode, String)] = List(
    Usage -> "usage",
    Refused -> "refused",
    Unreachable -> "unreachable",
    NotFound -> "not found",
    Conflict -> "conflict",
    Unavailable -> "unavailable",
    Fault -> "fault",
    OutcomeUnknown -> "outcome unknown"
  )

  extension (code: ExitCode) def exit(): Nothing = sys.exit(code.value)

  enum Origin derives CanEqual:
    /** The CLI found it itself: a bad flag, an unreadable local file, the server out of reach. */
    case Local

    case Server

  def of(problems: Problems, origin: Origin): ExitCode =
    val codes = problems.toVector.map(of(_, origin))
    if codes.contains(OutcomeUnknown) then OutcomeUnknown
    else if codes.contains(Unreachable) then Unreachable
    else codes.head

  def of(problem: Problem, origin: Origin): ExitCode =
    val local = origin == Origin.Local
    problem match
      case _: Invalid                     => Usage
      case _: NotFound                    => NotFound
      case _: Problem.Conflict.Busy       => Unavailable
      case _: Problem.Conflict.Undrained  => Unavailable
      case _: Problem.Conflict.ServerHeld => Unavailable
      case _: Conflict                    => Conflict
      case _: Problem.Refused.Quota       => Unavailable
      case _: Refused                     => Refused
      case _: Upstream.Unanswered         => if local then OutcomeUnknown else Unavailable
      case _: Upstream                    => if local then Unreachable else Unavailable
      case Io.Wrong(_, Io.Kind.Inside(_)) => Refused
      case _: Io.Process                  => Refused
      case _: Io                          => if local then Usage else Fault
      case _: Config                      => if local then Usage else Fault
      case _: Plugin.Rejected             => Refused
      case Plugin.Said(_, claim, _)       =>
        claim match
          case Plugin.Claim.Unavailable   => Unavailable
          case Plugin.Claim.Rejected      => Refused
          case Plugin.Claim.Misconfigured => Fault
          case Plugin.Claim.Failed        => Unavailable
      case _: Plugin               => Fault
      case _: Run.Paused           => Unavailable
      case _: Run.Cooling          => Unavailable
      case _: Run.Draining         => Unavailable
      case _: Run.LimitReached     => Unavailable
      case _: Run.AllowanceReached => Unavailable
      case _: Run                  => Refused
      case _: Content              => Refused
      case _: Internal             => Fault
      case _: Stored               => Fault
      case _: Unclassified         => if local then Usage else Refused
      case _: Relayed              => Refused
