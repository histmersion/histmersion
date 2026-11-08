package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.catalogue.{EventEdge, EventParticipant}
import com.histmersion.api.research.Article
import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.constraint.any.Pure

final case class DeleteBlocker(kind: DeleteBlocker.Kind, id: DeleteBlocker.Id, title: Option[DeleteBlocker.Title] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
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

  object Kind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]


    given WireEnumValues[Kind]:
      def all: Array[Kind] = values
      def wire(e: Kind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      def fromWire(s: WireEnumValues.Wire): Option[Kind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
    given Encoder[Kind] = WireEnum.encoder
    given Decoder[Kind] = WireEnum.decoder
    given Schema[Kind] = WireEnum.schema
