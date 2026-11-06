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
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    val allen: Set[com.histmersion.domain.catalogue.Event.EdgeKind.Wire] = Set(_root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Before), _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Meets), _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Overlaps), _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(During), _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Starts), _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Finishes), _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Equals))
    val causal: Set[com.histmersion.domain.catalogue.Event.EdgeKind.Wire] = Set(_root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Causes), _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Enables), _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Triggers), _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(RespondsTo), _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(FallsWithin))
    val all: List[com.histmersion.domain.catalogue.Event.EdgeKind.Wire] =
      List(
        _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Before),
        _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Meets),
        _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Overlaps),
        _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(During),
        _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Starts),
        _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Finishes),
        _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Equals),
        _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Causes),
        _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Enables),
        _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(Triggers),
        _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(RespondsTo),
        _root_.com.histmersion.domain.catalogue.Event.EdgeKind.Wire(FallsWithin)
      )

  /** Where a candidate link between an event and a source stands. */
  object LinkStatus:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Proposed = "proposed"
    val Confirmed = "confirmed"
    val Rejected = "rejected"
    val all: List[com.histmersion.domain.catalogue.Event.LinkStatus.Wire] = List(_root_.com.histmersion.domain.catalogue.Event.LinkStatus.Wire(Proposed), _root_.com.histmersion.domain.catalogue.Event.LinkStatus.Wire(Confirmed), _root_.com.histmersion.domain.catalogue.Event.LinkStatus.Wire(Rejected))
