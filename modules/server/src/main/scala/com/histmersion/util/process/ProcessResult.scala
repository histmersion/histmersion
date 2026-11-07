package com.histmersion.util.process

/** How a child process that was waited for ended: its exit code and its output, line by line. */
final case class ProcessResult(exitCode: ProcessResult.ExitCode, lines: List[ProcessResult.Lines]):
  def succeeded: Boolean = exitCode == 0

object ProcessResult:

  type ExitCode = ExitCode.T
  object ExitCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lines = Lines.T
  object Lines extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
