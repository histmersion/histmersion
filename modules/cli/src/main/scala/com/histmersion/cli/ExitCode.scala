package com.histmersion
package cli

import io.github.iltotore.iron.constraint.any.Pure

import util.problem.{Problem, Problems}
import util.problem.Problem.*

type ExitCode = ExitCode.T

/** The CLI's exit codes (AGENTS.md), spelled once: `main` turns a `Left(CliError)` into one of these. Success is 0.
  */
object ExitCode extends Newtype[Int, Pure]:
  /** The command line itself is wrong (a missing file, a bad flag value, "aborted"). */
  val Usage: ExitCode = ExitCode(1)

  /** The server said no — a refused write, a failed job, a failed backup. */
  val Refused: ExitCode = ExitCode(2)

  /** The server could not be reached, or the CLI could not start. */
  val Unreachable: ExitCode = ExitCode(3)

  /** Where the CLI met a failure: that, not the failure's group, decides the exit code. */
  enum Origin:
    /** The CLI found it itself: a bad flag, an unreadable local file, the server out of reach. */
    case Local

    /** The server answered with it. */
    case Server

  /** The exit code of a command that failed with `problems`, met at `origin`. */
  def of(problems: Problems, origin: Origin): ExitCode = origin match
    case Origin.Server => Refused
    case Origin.Local  => if problems.exists(_.localExit == Unreachable) then Unreachable else Usage

  extension (problem: Problem)
    /** The exit code of a problem the CLI met itself: the server out of reach (or answering with no problems) is
      * [[Unreachable]], anything else the command line's fault.
      */
    def localExit: ExitCode = problem match
      case _: Upstream     => Unreachable
      case _: Invalid      => Usage
      case _: NotFound     => Usage
      case _: Conflict     => Usage
      case _: Refused      => Usage
      case _: Io           => Usage
      case _: Config       => Usage
      case _: Plugin       => Usage
      case _: Run          => Usage
      case _: Content      => Usage
      case _: Internal     => Usage
      case _: Unclassified => Usage
      case _: Stored       => Usage
