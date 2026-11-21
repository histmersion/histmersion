package com.histmersion
package domain.catalogue

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match

object Event:

  type Id = Id.T

  object Id extends Newtype[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]

  type Uid = Uid.T

  object Uid extends Newtype[java.util.UUID, Pure]

  object EdgeKind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

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
    val allen: Set[Wire] =
      Set(Wire(Before), Wire(Meets), Wire(Overlaps), Wire(During), Wire(Starts), Wire(Finishes), Wire(Equals))
    val causal: Set[Wire] = Set(Wire(Causes), Wire(Enables), Wire(Triggers), Wire(RespondsTo), Wire(FallsWithin))
    val all: List[Wire] =
      List(
        Wire(Before),
        Wire(Meets),
        Wire(Overlaps),
        Wire(During),
        Wire(Starts),
        Wire(Finishes),
        Wire(Equals),
        Wire(Causes),
        Wire(Enables),
        Wire(Triggers),
        Wire(RespondsTo),
        Wire(FallsWithin)
      )

  object LinkStatus:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    val Proposed = "proposed"
    val Confirmed = "confirmed"
    val Rejected = "rejected"
    val all: List[Wire] = List(Wire(Proposed), Wire(Confirmed), Wire(Rejected))
