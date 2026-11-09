package com.histmersion
package domain.automation.agents
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import io.github.iltotore.iron.constraint.any.Pure

/** A provider's failure that is already a whole description (`RuntimeException: Claude API error: Overloaded`): a run's
  * error and the log show it as it is, where any other exception is shown as `<its class>: <its message>`.
  */
final class VerbatimFailure(val description: Problem) extends java.io.IOException(description.sentence)

object VerbatimFailure:

  /** How a failed provider call is described: a verbatim failure as it is, any other exception by class and message. */

  def describe(e: Throwable): Problem = e match
    case v: VerbatimFailure => v.description
    case _ => Problem.Unclassified(Legacy.Text(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}"))
