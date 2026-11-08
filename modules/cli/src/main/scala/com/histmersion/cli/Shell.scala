package com.histmersion
package cli

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Running an external program. Behind a trait so the parsers and the guards can be tested without a Calibre
  * installation.
  */
trait Shell:
  def run(cmd: List[String]): Either[Shell.Problem, String]
  def isRunning(processMatch: Shell.ProcessMatch): Boolean

object Shell:
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
  type ProcessMatch = ProcessMatch.T
  object ProcessMatch extends RefinedType[String, Pure]
  /** The real one: `java.lang.ProcessBuilder`, stdout captured, stderr folded into the error. */

  object System extends Shell:

    def run(cmd: List[String]): Either[Problem, String] =
      scala.util
        .Try {
          val p = new ProcessBuilder(cmd.asJava).redirectErrorStream(false).start()
          val out = new String(p.getInputStream.readAllBytes, "UTF-8")
          val err = new String(p.getErrorStream.readAllBytes, "UTF-8")
          val rc = p.waitFor()
          if rc == 0 then Right(out) else Left(Problem(s"${cmd.head} exited $rc: ${err.trim.take(400)}"))
        }
        .fold[Either[Problem, String]](
          e => Left(Problem(s"${cmd.headOption.getOrElse("command")} could not be run: ${e.getMessage}")),
          identity
        )

    def isRunning(processMatch: ProcessMatch): Boolean =
      run(List("pgrep", "-f", processMatch.value)).exists(_.trim.nonEmpty)
