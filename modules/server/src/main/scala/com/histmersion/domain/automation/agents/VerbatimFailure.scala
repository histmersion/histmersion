package com.histmersion.domain.automation.agents

/** A provider's failure that is already a whole description (`RuntimeException: Claude API error: Overloaded`): a run's
  * error and the log show it as it is, where any other exception is shown as `<its class>: <its message>`.
  */
final class VerbatimFailure(val description: VerbatimFailure.Description) extends java.io.IOException(description.value)

object VerbatimFailure:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** How a failed provider call is described: a verbatim failure as it is, any other exception by class and message. */
  type Description = com.histmersion.domain.automation.agents.VerbatimFailure.Problem
  val Description: _root_.com.histmersion.domain.automation.agents.VerbatimFailure.Problem.type = _root_.com.histmersion.domain.automation.agents.VerbatimFailure.Problem


  def describe(e: Throwable): com.histmersion.domain.automation.agents.VerbatimFailure.Problem = e match
    case v: VerbatimFailure => v.description
    case _                  => Problem(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}")
