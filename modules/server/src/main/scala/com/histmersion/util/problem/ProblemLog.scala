package com.histmersion
package util.problem

import util.problem.Text.sentence
import com.histmersion.api.log.LogEvent
import com.histmersion.util.log.Tracing

/** A problem in the server's log: `<group>.<case> <field>=<value> …: <sentence>`, the keys being the case's own field
  * names. Outside text appears here in full; an internal problem is logged as an error, any other as a warning. Inside
  * a trace, the problem is also one `ProblemRaised` event of the structured log: its case and, for an unexpected one,
  * its id, never its text.
  */
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
      record(problem)
      problem match
        case _: Problem.Internal | _: Problem.Unclassified | _: Problem.Stored => scribe.error(problem.line)
        case _                                                                 => scribe.warn(problem.line)

  /** An exception met at `where`, as the problem the caller answers with: a new problem id is minted, the exception is
    * logged under it with its stack, and the problem names the id, so the answer leads to the log line.
    */
  def unexpected(thrown: Throwable, where: Problem.Where): Problem =
    val problem = Problem.from(thrown, where, Problem.Internal.Id.mint())
    record(problem)
    scribe.error(problem.line, thrown)
    problem

  extension (problems: Problems)
    /** Writes every problem's line to the log. */
    def log(): Unit = problems.toVector.foreach(_.log())

  /** The problem's event in the calling thread's trace: its `<group>.<case>`, and the id of an unexpected one. */
  def record(problem: Problem): Unit =
    val id = problem match
      case u: Problem.Internal.Unexpected => Some(LogEvent.ProblemId(u.id.value))
      case _                              => None
    Tracing.record(
      LogEvent.What.ProblemRaised(id, LogEvent.ProblemCase(s"${problem.group.productPrefix}.${problem.productPrefix}")),
      id.map(i => LogEvent.Subject.Problem(LogEvent.Key(i.value))).toVector
    )

  /** A value as the log line shows it: quoted when it holds a space, a quote or an equals sign. */
  private def quoted(value: String): String =
    if value.exists(c => c.isWhitespace || c == '"' || c == '=') then s"\"${value.replace("\"", "\\\"")}\"" else value
