package com.histmersion.api.catalogue
object Historicity:
  val Attested = "attested"
  val Inferred = "inferred"
  val Contested = "contested"
  val Legendary = "legendary"
  val Mythic = "mythic"
  val all: List[String] = List(Attested, Inferred, Contested, Legendary, Mythic)
