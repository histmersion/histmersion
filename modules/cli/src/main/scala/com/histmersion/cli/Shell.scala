package com.histmersion.cli

import scala.jdk.CollectionConverters.*

/** Running an external program. Behind a trait so the parsers and the guards can be tested without a Calibre
  * installation.
  */
trait Shell:
  def run(cmd: List[com.histmersion.cli.Shell.RunCmd]): Either[com.histmersion.cli.Shell.RunResultPart1, com.histmersion.cli.Shell.RunResultPart2]
  def isRunning(processMatch: com.histmersion.cli.Shell.IsRunningProcessMatch): com.histmersion.cli.Shell.IsRunningResult

object Shell:
  /** The real one: `java.lang.ProcessBuilder`, stdout captured, stderr folded into the error. */
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

  object System extends Shell:
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

    def run(cmd: List[com.histmersion.cli.Shell.System.RunCmd]): Either[com.histmersion.cli.Shell.System.RunResultPart1, com.histmersion.cli.Shell.System.RunResultPart2] =
      scala.util
        .Try {
          val p = new ProcessBuilder(cmd.asJava).redirectErrorStream(false).start()
          val out = new String(p.getInputStream.readAllBytes, "UTF-8")
          val err = new String(p.getErrorStream.readAllBytes, "UTF-8")
          val rc = p.waitFor()
          if rc == 0 then Right(out) else Left(s"${cmd.head} exited $rc: ${err.trim.take(400)}")
        }
        .fold(e => Left(s"${cmd.headOption.getOrElse("command")} could not be run: ${e.getMessage}"), identity)

    def isRunning(processMatch: com.histmersion.cli.Shell.System.IsRunningProcessMatch): com.histmersion.cli.Shell.System.IsRunningResult =
      run(List("pgrep", "-f", processMatch)).exists(_.trim.nonEmpty)
