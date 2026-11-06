package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.StrictJson
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class EventLinksRequest(recompute: EventLinksRequest.Recompute = _root_.com.histmersion.api.catalogue.EventLinksRequest.Recompute(false), confirm: List[EventLinksRequest.Confirm] = Nil, reject: List[EventLinksRequest.Reject] = Nil)
    derives KindlingsSchema, com.histmersion.api.json.Schema
object EventLinksRequest:

  type Recompute = Recompute.T
  object Recompute extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Confirm = Confirm.T
  object Confirm extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reject = Reject.T
  object Reject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given Codec.AsObject[EventLinksRequest] = StrictJson.codec(KindlingsCodecAsObject.derived[EventLinksRequest])
