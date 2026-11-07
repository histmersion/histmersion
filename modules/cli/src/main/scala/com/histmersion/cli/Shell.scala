package com.histmersion.cli

import scala.jdk.CollectionConverters.*

/** Running an external program. Behind a trait so the parsers and the guards can be tested without a Calibre
  * installation.
  */
trait Shell:
  def run(cmd: List[String]): Either[com.histmersion.cli.Shell.Problem, String]
  def isRunning(processMatch: com.histmersion.cli.Shell.ProcessMatch): Boolean

object Shell:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ProcessMatch = ProcessMatch.T
  object ProcessMatch extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** The real one: `java.lang.ProcessBuilder`, stdout captured, stderr folded into the error. */

  object System extends Shell:

    def run(cmd: List[String]): Either[com.histmersion.cli.Shell.Problem, String] =
      scala.util
        .Try {
          val p = new ProcessBuilder(cmd.asJava).redirectErrorStream(false).start()
          val out = new String(p.getInputStream.readAllBytes, "UTF-8")
          val err = new String(p.getErrorStream.readAllBytes, "UTF-8")
          val rc = p.waitFor()
          if rc == 0 then Right(out) else Left(s"${cmd.head} exited $rc: ${err.trim.take(400)}")
        }
        .fold(e => Left(s"${cmd.headOption.getOrElse("command")} could not be run: ${e.getMessage}"), identity)

    def isRunning(processMatch: com.histmersion.cli.Shell.ProcessMatch): Boolean =
      run(List("pgrep", "-f", processMatch)).exists(_.trim.nonEmpty)
