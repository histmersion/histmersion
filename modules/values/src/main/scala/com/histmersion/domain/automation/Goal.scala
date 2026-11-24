package com.histmersion
package domain.automation

import com.histmersion.WireEnumValues
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match
import com.histmersion.lint.{LintRule, tolerated}

object Goal:

  type Slug = Slug.T

  object Slug extends Newtype[String, Match["[a-z0-9][a-z0-9_.-]{0,120}"]]:
    extension (s: Slug) def trimmed: Slug = assume(s.value.trim)

  type Uid = Uid.T

  object Uid extends Newtype[java.util.UUID, Pure]

  object Status:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    val Planned = "planned"
    val Seeding = "seeding"
    val Compiled = "compiled"
    val Reviewed = "reviewed"
    val Done = "done"
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val all: List[Wire] = List(Wire(Planned), Wire(Seeding), Wire(Compiled), Wire(Reviewed), Wire(Done))

  enum LinkKind(val wire: LinkKind.Wire) derives CanEqual:
    case Source extends LinkKind(LinkKind.Wire("source"))
    case Article extends LinkKind(LinkKind.Wire("article"))
    case Query extends LinkKind(LinkKind.Wire("query"))
    case QueueItem extends LinkKind(LinkKind.Wire("queue-item"))

  object LinkKind:

    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    val SearchQuery: LinkKind = Query

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Option[LinkKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
    given WireEnumValues[LinkKind]:
      def all: Array[LinkKind] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: LinkKind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def fromWire(s: WireEnumValues.Wire): Option[LinkKind] = parse(Wire((s).value))
