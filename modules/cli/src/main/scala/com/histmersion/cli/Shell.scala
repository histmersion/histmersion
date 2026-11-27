package com.histmersion
package cli
import com.histmersion.util.problem.{Legacy, Problem}

import scala.Conversion.into
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}
import java.nio.file.Path

/** Running an external program. Behind a trait so the parsers and the guards can be tested without a Calibre
  * installation.
  */
@atBoundary(BoundaryKind.Process, "runs a shell command and returns its output")
trait Shell:
  def run(cmd: List[String]): Either[Problem, String]
  def isRunning(processMatch: Shell.ProcessMatch): Boolean

object Shell:
  type ProcessMatch = ProcessMatch.T
  object ProcessMatch extends Newtype[String, Pure]

  final class Arg private[Shell] (val text: String)

  object Arg:
    given Conversion[String, Arg] = Arg(_)
    given Conversion[Int, Arg] = number => Arg(s"$number")
    given Conversion[Long, Arg] = number => Arg(s"$number")
    given Conversion[Path, Arg] = path => Arg(s"$path")
    given lifted: [T] => (m: RefinedType.Mirror[T]) => (base: Conversion[m.BaseType, Arg]) => Conversion[T, Arg] =
      newtype => base(newtype.asInstanceOf[m.BaseType])

  def command(parts: into[Arg]*): List[String] = parts.map(_.text).toList

  /** The real one: `java.lang.ProcessBuilder`, stdout captured, stderr folded into the error. */

  object System extends Shell:

    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
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

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    def isRunning(processMatch: ProcessMatch): Boolean =
      run(command("pgrep", "-f", processMatch)).exists(_.trim.nonEmpty)
