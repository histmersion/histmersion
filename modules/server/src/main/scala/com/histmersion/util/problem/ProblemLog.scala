package com.histmersion
package util.problem

import api.problem.Text.sentence

/** A problem in the server's log: `<group>.<case> <field>=<value> …: <sentence>`, the keys being the case's own field
  * names. Outside text appears here in full; an internal problem is logged as an error, any other as a warning.
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
    def log(): Unit = problem match
      case _: Problem.Internal | _: Problem.Unclassified | _: Problem.Stored => scribe.error(problem.line)
      case _                                                                  => scribe.warn(problem.line)

  extension (problems: Problems)
    /** Writes every problem's line to the log. */
    def log(): Unit = problems.toVector.foreach(_.log())

  /** A value as the log line shows it: quoted when it holds a space, a quote or an equals sign. */
  private def quoted(value: String): String =
    if value.exists(c => c.isWhitespace || c == '"' || c == '=') then s"\"${value.replace("\"", "\\\"")}\"" else value
