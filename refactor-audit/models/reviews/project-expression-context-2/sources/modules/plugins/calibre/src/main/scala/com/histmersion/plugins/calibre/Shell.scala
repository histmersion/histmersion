package com.histmersion.plugins.calibre

import scala.jdk.CollectionConverters.*

/** Running an external program. Behind a trait so the parsers and the guards can be tested without a Calibre
  * installation.
  */
trait Shell:
  def run(cmd: List[com.histmersion.plugins.calibre.Shell.RunCmd]): Either[com.histmersion.plugins.calibre.Shell.RunResultPart1, com.histmersion.plugins.calibre.Shell.RunResultPart2]
  def isRunning(processMatch: com.histmersion.plugins.calibre.Shell.IsRunningProcessMatch): com.histmersion.plugins.calibre.Shell.IsRunningResult

object Shell:
  /** The real one: `java.lang.ProcessBuilder`, stdout captured, stderr folded into the error. */
  type SystemRunResultPart1 = SystemRunResultPart1.T
  object SystemRunResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SystemRunResultPart2 = SystemRunResultPart2.T
  object SystemRunResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SystemRunCmd = SystemRunCmd.T
  object SystemRunCmd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SystemIsRunningResult = SystemIsRunningResult.T
  object SystemIsRunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SystemIsRunningProcessMatch = SystemIsRunningProcessMatch.T
  object SystemIsRunningProcessMatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type RunResultPart1 = RunResultPart1.T
  object RunResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunResultPart2 = RunResultPart2.T
  object RunResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RunCmd = RunCmd.T
  object RunCmd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsRunningResult = IsRunningResult.T
  object IsRunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsRunningProcessMatch = IsRunningProcessMatch.T
  object IsRunningProcessMatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def system(using processes: com.histmersion.plugin.Processes): Shell = new Shell:
    def run(cmd: List[com.histmersion.plugins.calibre.Shell.SystemRunCmd]): Either[com.histmersion.plugins.calibre.Shell.SystemRunResultPart1, com.histmersion.plugins.calibre.Shell.SystemRunResultPart2] =
      processes.capture(cmd).flatMap(result =>
        if result.exit == 0 then Right(result.out)
        else Left(s"${cmd.head} exited ${result.exit}: ${result.err.trim.take(400)}")
      )
    def isRunning(processMatch: com.histmersion.plugins.calibre.Shell.SystemIsRunningProcessMatch): com.histmersion.plugins.calibre.Shell.SystemIsRunningResult = run(List(_root_.com.histmersion.plugins.calibre.Shell.SystemRunCmd("pgrep"), _root_.com.histmersion.plugins.calibre.Shell.SystemRunCmd("-f"), processMatch)).exists(_.trim.nonEmpty)
