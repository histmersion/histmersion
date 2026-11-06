package com.histmersion.api.catalogue
object Historicity:
  type All = All.T
  object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Attested = "attested"
  val Inferred = "inferred"
  val Contested = "contested"
  val Legendary = "legendary"
  val Mythic = "mythic"
  val all: List[com.histmersion.api.catalogue.Historicity.All] = List(_root_.com.histmersion.api.catalogue.Historicity.All(Attested), _root_.com.histmersion.api.catalogue.Historicity.All(Inferred), _root_.com.histmersion.api.catalogue.Historicity.All(Contested), _root_.com.histmersion.api.catalogue.Historicity.All(Legendary), _root_.com.histmersion.api.catalogue.Historicity.All(Mythic))
