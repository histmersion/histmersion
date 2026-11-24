package com.histmersion
package util.process

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** How a child process that was waited for ended: its exit code and its output, line by line. */
final case class ProcessResult(exitCode: ProcessResult.ExitCode, lines: List[ProcessResult.Lines]):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def succeeded: Boolean = exitCode.value == 0

object ProcessResult:

  type ExitCode = ExitCode.T
  object ExitCode extends Newtype[Int, Pure]
  type Lines = Lines.T
  object Lines extends Newtype[String, Pure]
