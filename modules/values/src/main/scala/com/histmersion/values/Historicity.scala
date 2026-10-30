package com.histmersion.values

/** How firmly an event is known to have happened, as the store and the wire spell it. */
object Historicity:
  val Attested = "attested"
  val Inferred = "inferred"
  val Contested = "contested"
  val Legendary = "legendary"
  val Mythic = "mythic"
  val all: List[String] = List(Attested, Inferred, Contested, Legendary, Mythic)
