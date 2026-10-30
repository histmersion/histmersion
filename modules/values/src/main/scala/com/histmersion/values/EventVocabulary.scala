package com.histmersion.values

/** How firmly an event is known to have happened, as the store and the wire spell it. */
object Historicity:
  val Attested = "attested"
  val Inferred = "inferred"
  val Contested = "contested"
  val Legendary = "legendary"
  val Mythic = "mythic"
  val all: List[String] = List(Attested, Inferred, Contested, Legendary, Mythic)

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

/** Where a candidate link between an event and a source stands. */
object EventLinkStatus:
  val Proposed = "proposed"
  val Confirmed = "confirmed"
  val Rejected = "rejected"
  val all: List[String] = List(Proposed, Confirmed, Rejected)
