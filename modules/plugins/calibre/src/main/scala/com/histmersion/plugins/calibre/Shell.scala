package com.histmersion
package plugins.calibre
import com.histmersion.util.problem.{Legacy, Problem}

import scala.jdk.CollectionConverters.*
import com.histmersion.plugin.Processes
import io.github.iltotore.iron.constraint.any.Pure

/** Running an external program. Behind a trait so the parsers and the guards can be tested without a Calibre
  * installation.
  */
trait Shell:
  def run(cmd: List[Processes.RunArgv]): Either[Problem, Processes.Result.Out]
  def isRunning(processMatch: Shell.ProcessMatch): Boolean

object Shell:
  type ProcessMatch = ProcessMatch.T
  object ProcessMatch extends Newtype[String, Pure]

  /** The real one: `java.lang.ProcessBuilder`, stdout captured, stderr folded into the error. */

  def system(using processes: Processes): Shell = new Shell:
    def run(cmd: List[Processes.RunArgv]): Either[Problem, Processes.Result.Out] =
      processes
        .capture(cmd.map(a => Processes.CaptureArgv(a.value)))
        .flatMap(result =>
          if result.exit.value == 0 then Right(result.out)
          else
            Left(
              Problem.Unclassified(
                Legacy.Text(s"${cmd.head.value} exited ${result.exit.value}: ${result.err.value.trim.take(400)}")
              )
            )
        )
    def isRunning(processMatch: ProcessMatch): Boolean = run(
      List(Processes.RunArgv("pgrep"), Processes.RunArgv("-f"), Processes.RunArgv(processMatch.value))
    ).exists(_.value.trim.nonEmpty)
