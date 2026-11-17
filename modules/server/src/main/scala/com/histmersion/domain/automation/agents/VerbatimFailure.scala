package com.histmersion
package domain.automation.agents
import com.histmersion.util.problem.{External, Problem}
import com.histmersion.util.problem.Text.sentence

import io.github.iltotore.iron.constraint.any.Pure

/** A provider's failure that is already a whole description (`RuntimeException: Claude API error: Overloaded`): a run's
  * error and the log show it as it is, where any other exception is shown as `<its class>: <its message>`.
  */
final class VerbatimFailure(val description: Problem) extends java.io.IOException(description.sentence)

object VerbatimFailure:

  /** How a failed call to `model` is described: a verbatim failure as it is, any other exception by class and message.
    */
  def describe(model: Problem.Upstream.Name, e: Throwable): Problem = e match
    case v: VerbatimFailure => Problem.Upstream.CallFailed(model, v.description)
    case _                  =>
      Problem.Upstream.CallThrew(
        model,
        External.Reported.of(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}")
      )
