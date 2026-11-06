package com.histmersion.cli

import com.histmersion.config.*

/** The CLI's exit codes (AGENTS.md), spelled once: `main` turns a `Left(CliError)` into one of these. Success is 0.
  */
object ExitCode:
  /** The command line itself is wrong (a missing file, a bad flag value, "aborted"). */
  type Usage = UsageValue
  type UsageValue = UsageValue.T
  object UsageValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Refused = RefusedValue
  type RefusedValue = RefusedValue.T
  object RefusedValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unreachable = UnreachableValue
  type UnreachableValue = UnreachableValue.T
  object UnreachableValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Usage: com.histmersion.cli.ExitCode.Usage = 1

  /** The server said no — a refused write, a failed job, a failed backup. */
  val Refused: com.histmersion.cli.ExitCode.Refused = 2

  /** The server could not be reached, or the CLI could not start. */
  val Unreachable: com.histmersion.cli.ExitCode.Unreachable = 3
