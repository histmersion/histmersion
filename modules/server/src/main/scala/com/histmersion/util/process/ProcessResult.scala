package com.histmersion.util.process

/** How a child process that was waited for ended: its exit code and its output, line by line. */
final case class ProcessResult(exitCode: ProcessResult.ExitCode, lines: List[ProcessResult.Lines]):
  def succeeded: com.histmersion.util.process.ProcessResult.SucceededResult = exitCode == 0

object ProcessResult:
  type SucceededResult = SucceededResult.T
  object SucceededResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ExitCode = ExitCode.T
  object ExitCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Lines = Lines.T
  object Lines extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
