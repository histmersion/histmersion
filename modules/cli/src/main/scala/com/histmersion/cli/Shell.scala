package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure

/** Running an external program. Behind a trait so the parsers and the guards can be tested without a Calibre
  * installation.
  */
trait Shell:
  def run(cmd: List[String]): Either[Problem, String]
  def isRunning(processMatch: Shell.ProcessMatch): Boolean

object Shell:
  type ProcessMatch = ProcessMatch.T
  object ProcessMatch extends Newtype[String, Pure]

  /** The real one: `java.lang.ProcessBuilder`, stdout captured, stderr folded into the error. */

  object System extends Shell:

    def run(cmd: List[String]): Either[Problem, String] =
      scala.util
        .Try {
          val p = new ProcessBuilder(cmd.asJava).redirectErrorStream(false).start()
          val out = new String(p.getInputStream.readAllBytes, "UTF-8")
          val err = new String(p.getErrorStream.readAllBytes, "UTF-8")
          val rc = p.waitFor()
          if rc == 0 then Right(out)
          else Left(Problem.Unclassified(Legacy.Text(s"${cmd.head} exited $rc: ${err.trim.take(400)}")))
        }
        .fold[Either[Problem, String]](
          e =>
            Left(
              Problem
                .Unclassified(Legacy.Text(s"${cmd.headOption.getOrElse("command")} could not be run: ${e.getMessage}"))
            ),
          identity
        )

    def isRunning(processMatch: ProcessMatch): Boolean =
      run(List("pgrep", "-f", processMatch.value)).exists(_.trim.nonEmpty)
