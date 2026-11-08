package com.histmersion
package domain.automation

import com.histmersion.WireEnumValues
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match

object Goal:

  /** A goal's slug: the article slug's rule, but its own type, so a goal never passes for an article. */
  type Slug = Slug.T

  object Slug extends RefinedType[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]:
    extension (s: Slug) def trimmed: Slug = assume(s.value.trim)

  /** A goal's stable identity across renames, merges and databases (`goals.uid`). */
  type Uid = Uid.T

  object Uid extends RefinedType[java.util.UUID, Pure]

  /** The stages of a research goal, as the store and the wire spell them. */
  object Status:
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]

    val Planned = "planned"
    val Seeding = "seeding"
    val Compiled = "compiled"
    val Reviewed = "reviewed"
    val Done = "done"
    val all: List[Wire] = List(Wire(Planned), Wire(Seeding), Wire(Compiled), Wire(Reviewed), Wire(Done))

  /** What a goal is linked to. */
  enum LinkKind(val wire: LinkKind.Wire):
    case Source extends LinkKind(LinkKind.Wire("source"))
    case Article extends LinkKind(LinkKind.Wire("article"))
    case Query extends LinkKind(LinkKind.Wire("query"))
    case QueueItem extends LinkKind(LinkKind.Wire("queue-item"))

  object LinkKind:

    /** The search queries a goal's plan recorded ([[Query]]): the name the engine uses, which no other type shares. */
    type Wire = Wire.T
    object Wire extends RefinedType[String, Pure]


    val SearchQuery: LinkKind = Query

    def parse(s: Wire): Option[LinkKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
    given WireEnumValues[LinkKind]:
      def all: Array[LinkKind] = values
      def wire(e: LinkKind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      def fromWire(s: WireEnumValues.Wire): Option[LinkKind] = parse(Wire((s).value))
