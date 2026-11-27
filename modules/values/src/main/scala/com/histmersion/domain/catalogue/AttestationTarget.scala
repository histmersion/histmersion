package com.histmersion
package domain.catalogue

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
object AttestationTarget:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  val Finding = "finding"
  val Event = "event"
  val EventDating = "event_dating"
  val Place = "place"
  val Edge = "edge"
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val all: List[Wire] = List(Wire(Finding), Wire(Event), Wire(EventDating), Wire(Place), Wire(Edge))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
  def check(t: Wire): Either[Problem, Wire] =
    val n = Option((t).value).getOrElse("").trim.toLowerCase
    Either.cond(
      all.contains(n),
      Wire(n),
      Problem.Invalid.Unknown
        .here(Problem.TypeName.of(Wire), Problem.Raw(s"$t"), all.map(w => Problem.Raw(s"$w")).toVector)
    )
