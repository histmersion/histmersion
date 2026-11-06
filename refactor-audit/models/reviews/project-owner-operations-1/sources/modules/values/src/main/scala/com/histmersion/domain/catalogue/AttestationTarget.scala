package com.histmersion.domain.catalogue
object AttestationTarget:
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart1 = CheckResultPart1.T
  object CheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart2 = CheckResultPart2.T
  object CheckResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckT = CheckT.T
  object CheckT extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Finding = "finding"
  val Event = "event"
  val EventDating = "event_dating"
  val Place = "place"
  val Edge = "edge"
  val all: List[com.histmersion.domain.catalogue.AttestationTarget.All] = List(_root_.com.histmersion.domain.catalogue.AttestationTarget.All(Finding), _root_.com.histmersion.domain.catalogue.AttestationTarget.All(Event), _root_.com.histmersion.domain.catalogue.AttestationTarget.All(EventDating), _root_.com.histmersion.domain.catalogue.AttestationTarget.All(Place), _root_.com.histmersion.domain.catalogue.AttestationTarget.All(Edge))

  def check(t: com.histmersion.domain.catalogue.AttestationTarget.CheckT): Either[com.histmersion.domain.catalogue.AttestationTarget.CheckResultPart1, com.histmersion.domain.catalogue.AttestationTarget.CheckResultPart2] =
    val n = Option((t).value).getOrElse("").trim.toLowerCase
    Either.cond(all.contains(n), n, _root_.com.histmersion.domain.catalogue.AttestationTarget.CheckResultPart1(s"unknown target type '$t' (${all.mkString(", ")})"))
