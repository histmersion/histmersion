package com.histmersion
package cli

import util.problem.Problems

object ExitCode:
  val Usage = 1
  val Refused = 2
  val Unreachable = 3

/** The shape of `Cli.Error`: the problems, an exit code and a flag, with defaults. */
object Cli:
  final case class Error(problems: Problems, exitCode: Int = ExitCode.Unreachable, notFound: Boolean = false)
