package com.histmersion.values

/** The stages of a research goal, as the store and the wire spell them. */
object GoalStatus:
  val Planned = "planned"
  val Seeding = "seeding"
  val Compiled = "compiled"
  val Reviewed = "reviewed"
  val Done = "done"
  val all: List[String] = List(Planned, Seeding, Compiled, Reviewed, Done)
