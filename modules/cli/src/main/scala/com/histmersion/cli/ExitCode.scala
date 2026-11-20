package com.histmersion
package cli

import io.github.iltotore.iron.constraint.any.Pure

import util.problem.{Problem, Problems}
import util.problem.Problem.*

type ExitCode = ExitCode.T

/** The CLI's exit codes, spelled once and worked out from the problem (owner decision E15): a command that fails exits
  * with the code of its problem's case and of where it was met ([[Origin]]), never with a code chosen at the site.
  * Success is 0. Each code says what a caller does next; [[documented]] lists them, as the CLI reference does.
  */
object ExitCode extends Newtype[Int, Pure]:

  /** The command, its input or this machine's setup is wrong: sent again unchanged, it fails again. */
  val Usage: ExitCode = ExitCode(1)

  /** The server refused the request, or the work failed (a rule, a policy, a failed job, a tool that exited badly). */
  val Refused: ExitCode = ExitCode(2)

  /** The server could not be reached: nothing was applied, sending it again is safe. */
  val Unreachable: ExitCode = ExitCode(3)

  /** What the command names does not exist. */
  val NotFound: ExitCode = ExitCode(4)

  /** The record is not in the state the command expects (changed since it was read, already there, in use): read it
    * again, then decide.
    */
  val Conflict: ExitCode = ExitCode(5)

  /** The server or a service it needs is busy, under maintenance, out of allowance or down for now: try again later.
    */
  val Unavailable: ExitCode = ExitCode(6)

  /** A fault: a bug, damaged stored data, or a server that cannot work as it is set up. Sending it again does not help;
    * the message and the server's log say what failed.
    */
  val Fault: ExitCode = ExitCode(7)

  /** A write was sent and no answer came (the connection ended, the wait ran out): it may have been applied. Check
    * before sending it again.
    */
  val OutcomeUnknown: ExitCode = ExitCode(8)

  /** Every exit code a failed command may end with, with its name and what it means, in order: what the CLI reference
    * (`docs/user-guide/reference/cli.md`) lists, and what a check holds it to.
    */
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

  /** Where the CLI met a problem. The same case can be the caller's own input on this machine and the server's state: a
    * file that cannot be read here is the command's to fix, one the server cannot read is the server's fault.
    */
  enum Origin derives CanEqual:
    /** The CLI found it itself: a bad flag, an unreadable local file, the server out of reach. */
    case Local

    /** The server answered with it: a refusal, or the outcome of work the server did (a job, a verification). */
    case Server

  /** The exit code of a command that failed with `problems`, met at `origin`: a write whose outcome is unknown first,
    * then the server out of reach, else the first problem's code (as the HTTP status is the first problem's).
    */
  def of(problems: Problems, origin: Origin): ExitCode =
    val codes = problems.toVector.map(of(_, origin))
    if codes.contains(OutcomeUnknown) then OutcomeUnknown
    else if codes.contains(Unreachable) then Unreachable
    else codes.head

  /** The exit code of one problem, met at `origin`: total over the cases. */
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
