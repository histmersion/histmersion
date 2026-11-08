package com.histmersion
package domain.automation.agents

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A provider's failure that is already a whole description (`RuntimeException: Claude API error: Overloaded`): a run's
  * error and the log show it as it is, where any other exception is shown as `<its class>: <its message>`.
  */
final class VerbatimFailure(val description: VerbatimFailure.Description) extends java.io.IOException(description.value)

object VerbatimFailure:
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
  /** How a failed provider call is described: a verbatim failure as it is, any other exception by class and message. */
  type Description = Problem
  val Description: Problem.type = Problem


  def describe(e: Throwable): Problem = e match
    case v: VerbatimFailure => v.description
    case _                  => Problem(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}")
