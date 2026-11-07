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
      processes.capture(cmd).flatMap(result =>
        if result.exit == 0 then Right(result.out)
        else Left(s"${cmd.head} exited ${result.exit}: ${result.err.trim.take(400)}")
      )
    def isRunning(processMatch: com.histmersion.plugins.calibre.Shell.ProcessMatch): Boolean = run(List(_root_.com.histmersion.plugin.Processes.RunArgv("pgrep"), _root_.com.histmersion.plugin.Processes.RunArgv("-f"), processMatch)).exists(_.trim.nonEmpty)
