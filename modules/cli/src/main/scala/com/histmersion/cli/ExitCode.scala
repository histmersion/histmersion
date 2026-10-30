package com.histmersion.cli

import com.histmersion.api.*
import com.histmersion.config.*

/** The CLI's exit codes (AGENTS.md), spelled once: `main` turns a `Left(CliError)` into one of these. Success is 0.
  */
object ExitCode:
  /** The command line itself is wrong (a missing file, a bad flag value, "aborted"). */
  val Usage: Int = 1

  /** The server said no — a refused write, a failed job, a failed backup. */
  val Refused: Int = 2

  /** The server could not be reached, or the CLI could not start. */
  val Unreachable: Int = 3
