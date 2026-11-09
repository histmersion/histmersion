package com.histmersion
package domain.catalogue

import com.histmersion.util.problem.{Legacy, Problem}
import io.github.iltotore.iron.constraint.any.Pure
object AttestationTarget:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  val Finding = "finding"
  val Event = "event"
  val EventDating = "event_dating"
  val Place = "place"
  val Edge = "edge"
  val all: List[Wire] = List(Wire(Finding), Wire(Event), Wire(EventDating), Wire(Place), Wire(Edge))

  def check(t: Wire): Either[Problem, Wire] =
    val n = Option((t).value).getOrElse("").trim.toLowerCase
    Either.cond(
      all.contains(n),
      Wire(n),
      Problem.Unclassified(Legacy.Text(s"unknown target type '$t' (${all.mkString(", ")})"))
    )
