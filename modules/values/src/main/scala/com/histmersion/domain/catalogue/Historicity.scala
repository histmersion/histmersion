package com.histmersion.domain.catalogue
object Historicity:
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Attested = "attested"
  val Inferred = "inferred"
  val Contested = "contested"
  val Legendary = "legendary"
  val Mythic = "mythic"
  val all: List[com.histmersion.domain.catalogue.Historicity.All] = List(Attested, Inferred, Contested, Legendary, Mythic)
