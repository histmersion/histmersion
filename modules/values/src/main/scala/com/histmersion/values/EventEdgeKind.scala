package com.histmersion.values

/** The relations between two events: Allen's interval relations and the causal ones. */
object EventEdgeKind:
  val Before = "before"
  val Meets = "meets"
  val Overlaps = "overlaps"
  val During = "during"
  val Starts = "starts"
  val Finishes = "finishes"
  val Equals = "equals"
  val Causes = "causes"
  val Enables = "enables"
  val Triggers = "triggers"
  val RespondsTo = "responds_to"
  val FallsWithin = "falls_within"
  val allen: Set[String] = Set(Before, Meets, Overlaps, During, Starts, Finishes, Equals)
  val causal: Set[String] = Set(Causes, Enables, Triggers, RespondsTo, FallsWithin)
  val all: List[String] =
    List(Before, Meets, Overlaps, During, Starts, Finishes, Equals, Causes, Enables, Triggers, RespondsTo, FallsWithin)
