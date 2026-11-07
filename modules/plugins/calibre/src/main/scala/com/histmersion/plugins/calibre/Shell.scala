package com.histmersion.plugins.calibre

import scala.jdk.CollectionConverters.*

/** Running an external program. Behind a trait so the parsers and the guards can be tested without a Calibre
  * installation.
  */
trait Shell:
  def run(cmd: List[com.histmersion.plugin.Processes.RunArgv]): Either[com.histmersion.plugins.calibre.Shell.Problem, com.histmersion.plugin.Processes.Result.Out]
  def isRunning(processMatch: com.histmersion.plugins.calibre.Shell.ProcessMatch): Boolean

object Shell:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProcessMatch = ProcessMatch.T
  object ProcessMatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** The real one: `java.lang.ProcessBuilder`, stdout captured, stderr folded into the error. */


  def system(using processes: com.histmersion.plugin.Processes): Shell = new Shell:
    def run(cmd: List[com.histmersion.plugin.Processes.RunArgv]): Either[com.histmersion.plugins.calibre.Shell.Problem, com.histmersion.plugin.Processes.Result.Out] =
      processes
        .capture(cmd.map(a => com.histmersion.plugin.Processes.CaptureArgv(a.value)))
        .left
        .map(e => Problem(e.value))
        .flatMap(result =>
          if result.exit.value == 0 then Right(result.out)
          else Left(Problem(s"${cmd.head.value} exited ${result.exit.value}: ${result.err.value.trim.take(400)}"))
        )
    def isRunning(processMatch: com.histmersion.plugins.calibre.Shell.ProcessMatch): Boolean = run(List(_root_.com.histmersion.plugin.Processes.RunArgv("pgrep"), _root_.com.histmersion.plugin.Processes.RunArgv("-f"), _root_.com.histmersion.plugin.Processes.RunArgv(processMatch.value))).exists(_.value.trim.nonEmpty)
