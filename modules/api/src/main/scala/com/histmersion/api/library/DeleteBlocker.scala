package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.catalogue.{EventEdge, EventParticipant}
import com.histmersion.api.research.Article
import io.circe.{Decoder, Encoder}

final case class DeleteBlocker(kind: DeleteBlocker.Kind, id: DeleteBlocker.Id, title: Option[DeleteBlocker.Title] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
object DeleteBlocker:


  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Kind(val wire: Kind.Wire, val label: Kind.Label):
    case Article extends Kind(_root_.com.histmersion.api.library.DeleteBlocker.Kind.Wire("article"), _root_.com.histmersion.api.library.DeleteBlocker.Kind.Label("cited by article"))
    case Event extends Kind(_root_.com.histmersion.api.library.DeleteBlocker.Kind.Wire("event"), _root_.com.histmersion.api.library.DeleteBlocker.Kind.Label("primary source of event"))
    case EventEdge extends Kind(_root_.com.histmersion.api.library.DeleteBlocker.Kind.Wire("event-edge"), _root_.com.histmersion.api.library.DeleteBlocker.Kind.Label("source of event relation"))
    case EventParticipant extends Kind(_root_.com.histmersion.api.library.DeleteBlocker.Kind.Wire("event-participant"), _root_.com.histmersion.api.library.DeleteBlocker.Kind.Label("source of event participant"))
    case Job extends Kind(_root_.com.histmersion.api.library.DeleteBlocker.Kind.Wire("job"), _root_.com.histmersion.api.library.DeleteBlocker.Kind.Label("still being written by job"))

  object Kind:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given WireEnumValues[Kind]:
      def all: Array[Kind] = Kind.values
      def wire(e: Kind): _root_.com.histmersion.api.WireEnumValues.WireResult = _root_.com.histmersion.api.WireEnumValues.WireResult((e.wire).value)
      def fromWire(s: _root_.com.histmersion.api.WireEnumValues.FromWireS): _root_.scala.Option[Kind] = Kind.values.find(_.wire == _root_.com.histmersion.api.library.DeleteBlocker.Kind.Wire((s).value.trim.toLowerCase))
    given Encoder[Kind] = WireEnum.encoder
    given Decoder[Kind] = WireEnum.decoder
    given Schema[Kind] = WireEnum.schema
