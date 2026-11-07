package com.histmersion.cli

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

type ExitCode = ExitCode.T

/** The CLI's exit codes (AGENTS.md), spelled once: `main` turns a `Left(CliError)` into one of these. Success is 0.
  */
object ExitCode extends RefinedType[Int, Pure]:
  /** The command line itself is wrong (a missing file, a bad flag value, "aborted"). */
  val Usage: ExitCode = ExitCode(1)

  /** The server said no — a refused write, a failed job, a failed backup. */
  val Refused: ExitCode = ExitCode(2)

  /** The server could not be reached, or the CLI could not start. */
  val Unreachable: ExitCode = ExitCode(3)
