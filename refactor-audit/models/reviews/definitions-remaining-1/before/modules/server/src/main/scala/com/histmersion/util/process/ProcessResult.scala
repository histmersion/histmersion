package com.histmersion.util.process

/** How a child process that was waited for ended: its exit code and its output, line by line. */
final case class ProcessResult(exitCode: Int, lines: List[String]):
  def succeeded: Boolean = exitCode == 0
