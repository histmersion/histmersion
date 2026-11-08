package com.histmersion
package util.process

import io.github.iltotore.iron.constraint.any.Pure

/** How a child process that was waited for ended: its exit code and its output, line by line. */
final case class ProcessResult(exitCode: ProcessResult.ExitCode, lines: List[ProcessResult.Lines]):
  def succeeded: Boolean = exitCode.value == 0

object ProcessResult:

  type ExitCode = ExitCode.T
  object ExitCode extends Newtype[Int, Pure]
  type Lines = Lines.T
  object Lines extends Newtype[String, Pure]
