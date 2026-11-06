package com.histmersion.domain.catalogue
object AttestationTarget:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Finding = "finding"
  val Event = "event"
  val EventDating = "event_dating"
  val Place = "place"
  val Edge = "edge"
  val all: List[com.histmersion.domain.catalogue.AttestationTarget.Wire] = List(_root_.com.histmersion.domain.catalogue.AttestationTarget.Wire(Finding), _root_.com.histmersion.domain.catalogue.AttestationTarget.Wire(Event), _root_.com.histmersion.domain.catalogue.AttestationTarget.Wire(EventDating), _root_.com.histmersion.domain.catalogue.AttestationTarget.Wire(Place), _root_.com.histmersion.domain.catalogue.AttestationTarget.Wire(Edge))

  def check(t: com.histmersion.domain.catalogue.AttestationTarget.Wire): Either[com.histmersion.domain.catalogue.AttestationTarget.Problem, com.histmersion.domain.catalogue.AttestationTarget.Wire] =
    val n = Option((t).value).getOrElse("").trim.toLowerCase
    Either.cond(all.contains(n), _root_.com.histmersion.domain.catalogue.AttestationTarget.Wire(n), _root_.com.histmersion.domain.catalogue.AttestationTarget.Problem(s"unknown target type '$t' (${all.mkString(", ")})"))
