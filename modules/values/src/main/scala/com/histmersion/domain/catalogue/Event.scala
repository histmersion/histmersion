package com.histmersion.domain.catalogue

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match

object Event:

  /** An event's id, a slug (`uluburun_shipwreck`). */
  type Id = Id.T

  object Id extends RefinedType[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]

  /** An event's stable identity across renames, merges and databases (`events.uid`). */
  type Uid = Uid.T

  object Uid extends RefinedType[java.util.UUID, Pure]

  /** The relations between two events: Allen's interval relations and the causal ones. */
  object EdgeKind:
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
  object LinkStatus:
    val Proposed = "proposed"
    val Confirmed = "confirmed"
    val Rejected = "rejected"
    val all: List[String] = List(Proposed, Confirmed, Rejected)
