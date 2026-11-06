package com.histmersion.domain.catalogue
object Historicity:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Attested = "attested"
  val Inferred = "inferred"
  val Contested = "contested"
  val Legendary = "legendary"
  val Mythic = "mythic"
  val all: List[com.histmersion.domain.catalogue.Historicity.Wire] = List(_root_.com.histmersion.domain.catalogue.Historicity.Wire(Attested), _root_.com.histmersion.domain.catalogue.Historicity.Wire(Inferred), _root_.com.histmersion.domain.catalogue.Historicity.Wire(Contested), _root_.com.histmersion.domain.catalogue.Historicity.Wire(Legendary), _root_.com.histmersion.domain.catalogue.Historicity.Wire(Mythic))
