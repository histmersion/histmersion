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
    val Planned = "planned"
    val Seeding = "seeding"
    val Compiled = "compiled"
    val Reviewed = "reviewed"
    val Done = "done"
    val all: List[String] = List(Planned, Seeding, Compiled, Reviewed, Done)

  /** What a goal is linked to. */
  enum LinkKind(val wire: String):
    case Source extends LinkKind("source")
    case Article extends LinkKind("article")
    case Query extends LinkKind("query")
    case QueueItem extends LinkKind("queue-item")

  object LinkKind:

    /** The search queries a goal's plan recorded ([[Query]]): the name the engine uses, which no other type shares. */
    val SearchQuery: LinkKind = Query

    def parse(s: String): Option[LinkKind] = values.find(_.wire == s.trim.toLowerCase)
    given WireEnumValues[LinkKind]:
      def all: Array[LinkKind] = LinkKind.values
      def wire(e: LinkKind): String = e.wire
      def fromWire(s: String): Option[LinkKind] = parse(s)
