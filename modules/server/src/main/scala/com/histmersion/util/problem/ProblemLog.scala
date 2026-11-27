package com.histmersion
package util.problem

import util.problem.Text.sentence
import com.histmersion.api.log.LogEvent
import com.histmersion.util.log.TraceScope
import com.histmersion.lint.{atBoundary, BoundaryKind}

@atBoundary(BoundaryKind.Render, "writes a problem's log line: its group and case, its fields by name, its sentence")
object ProblemLog:

  extension (problem: Problem)
    /** The log line of the problem. */
    def line: String =
      val fields = problem.productElementNames
        .zip(problem.productIterator)
        .map((name, value) => s" $name=${quoted(s"$value")}")
        .mkString
      s"${problem.group.productPrefix}.${problem.productPrefix}$fields: ${problem.sentence}"

    /** Writes the problem's line to the log. */
    def log(): Unit =
      problem.record()
      problem match
        case _: Problem.Internal | _: Problem.Unclassified | _: Problem.Stored => scribe.error(problem.line)
        case _                                                                 => scribe.warn(problem.line)

    def record(): Unit =
      val id = problem match
        case u: Problem.Internal.Unexpected => Some(LogEvent.ProblemId.of(u.id))
        case _                              => None
      TraceScope.record(LogEvent.What.ProblemRaised(id, LogEvent.ProblemCase.of(problem)))

  /** An exception met at `where`, as the problem the caller answers with: a new problem id is minted, the exception is
    * logged under it with its stack, and the problem names the id, so the answer leads to the log line.
    */
  def unexpected(thrown: Throwable, where: Problem.Where): Problem =
    val problem = Problem.from(thrown, where, Problem.Internal.Id.mint())
    problem.record()
    scribe.error(problem.line, thrown)
    problem

  extension (problems: Problems)
    /** Writes every problem's line to the log. */
    def log(): Unit = problems.toVector.foreach(_.log())

  /** A value as the log line shows it: quoted when it holds a space, a quote or an equals sign. */
  private def quoted(value: String): String =
    if value.exists(c => c.isWhitespace || c == '"' || c == '=') then s"\"${value.replace("\"", "\\\"")}\"" else value
