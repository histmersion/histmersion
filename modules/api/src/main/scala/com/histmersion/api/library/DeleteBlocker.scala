package com.histmersion
package api.library
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.catalogue.{EventEdge, EventParticipant}
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.GeoPick

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
final case class DeleteBlocker(
    kind: DeleteBlocker.Kind,
    id: DeleteBlocker.Id,
    title: Option[DeleteBlocker.Title] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema
object DeleteBlocker:

  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]

  enum Kind(val wire: Kind.Wire, val label: Kind.Label):
    case Article extends Kind(Kind.Wire("article"), Kind.Label("cited by article"))
    case Event extends Kind(Kind.Wire("event"), Kind.Label("primary source of event"))
    case EventEdge extends Kind(Kind.Wire("event-edge"), Kind.Label("source of event relation"))
    case EventParticipant extends Kind(Kind.Wire("event-participant"), Kind.Label("source of event participant"))
    case Job extends Kind(Kind.Wire("job"), Kind.Label("still being written by job"))

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  object Kind:
    export GeoPick.Wire
    type Label = Label.T
    object Label extends Newtype[String, Pure]

    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def fromWire(s: WireEnumValues.Wire): Option[Kind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
    given JsonValueCodec[Kind] = WireEnum.codec
    given Schema[Kind] = WireEnum.schema
