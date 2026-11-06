package com.histmersion.domain.automation

import com.histmersion.WireEnumValues
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match

object Goal:

  /** A goal's slug: the article slug's rule, but its own type, so a goal never passes for an article. */
  type Slug = Slug.T

  object Slug extends RefinedType[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]

  /** A goal's stable identity across renames, merges and databases (`goals.uid`). */
  type Uid = Uid.T

  object Uid extends RefinedType[java.util.UUID, Pure]

  /** The stages of a research goal, as the store and the wire spell them. */
  object Status:
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Planned = "planned"
    val Seeding = "seeding"
    val Compiled = "compiled"
    val Reviewed = "reviewed"
    val Done = "done"
    val all: List[com.histmersion.domain.automation.Goal.Status.All] = List(_root_.com.histmersion.domain.automation.Goal.Status.All(Planned), _root_.com.histmersion.domain.automation.Goal.Status.All(Seeding), _root_.com.histmersion.domain.automation.Goal.Status.All(Compiled), _root_.com.histmersion.domain.automation.Goal.Status.All(Reviewed), _root_.com.histmersion.domain.automation.Goal.Status.All(Done))

  /** What a goal is linked to. */
  enum LinkKind(val wire: LinkKind.Wire):
    case Source extends LinkKind(_root_.com.histmersion.domain.automation.Goal.LinkKind.Wire("source"))
    case Article extends LinkKind(_root_.com.histmersion.domain.automation.Goal.LinkKind.Wire("article"))
    case Query extends LinkKind(_root_.com.histmersion.domain.automation.Goal.LinkKind.Wire("query"))
    case QueueItem extends LinkKind(_root_.com.histmersion.domain.automation.Goal.LinkKind.Wire("queue-item"))

  object LinkKind:

    /** The search queries a goal's plan recorded ([[Query]]): the name the engine uses, which no other type shares. */
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val SearchQuery: LinkKind = Query

    def parse(s: com.histmersion.domain.automation.Goal.LinkKind.ParseS): Option[LinkKind] = values.find(_.wire == (s).value.trim.toLowerCase)
    given WireEnumValues[LinkKind]:
      def all: Array[LinkKind] = LinkKind.values
      def wire(e: LinkKind): com.histmersion.domain.automation.Goal.LinkKind.WireResult = e.wire
      def fromWire(s: com.histmersion.domain.automation.Goal.LinkKind.FromWireS): Option[LinkKind] = parse(s)
