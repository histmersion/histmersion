package com.histmersion.domain.automation.agents

/** A provider's failure that is already a whole description (`RuntimeException: Claude API error: Overloaded`): a run's
  * error and the log show it as it is, where any other exception is shown as `<its class>: <its message>`.
  */
final class VerbatimFailure(val description: String) extends java.io.IOException(description)

object VerbatimFailure:
  /** How a failed provider call is described: a verbatim failure as it is, any other exception by class and message. */
  type DescribeResult = DescribeResult.T
  object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def describe(e: Throwable): com.histmersion.domain.automation.agents.VerbatimFailure.DescribeResult = e match
    case v: VerbatimFailure => v.description
    case _                  => s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}"
