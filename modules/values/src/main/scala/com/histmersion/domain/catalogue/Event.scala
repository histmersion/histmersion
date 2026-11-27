package com.histmersion
package domain.catalogue

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match
import com.histmersion.lint.{tolerated, LintRule}

object Event:

  type Id = Id.T

  object Id extends Newtype[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]

  type Uid = Uid.T

  object Uid extends Newtype[java.util.UUID, Pure]

  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val allen: Set[Wire] =
      Set(Wire(Before), Wire(Meets), Wire(Overlaps), Wire(During), Wire(Starts), Wire(Finishes), Wire(Equals))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val causal: Set[Wire] = Set(Wire(Causes), Wire(Enables), Wire(Triggers), Wire(RespondsTo), Wire(FallsWithin))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val all: List[Wire] = List(Wire(Proposed), Wire(Confirmed), Wire(Rejected))
