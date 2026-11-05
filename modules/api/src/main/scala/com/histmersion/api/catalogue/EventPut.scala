package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.{StrictJson, TimeSpan}
import com.histmersion.api.given

final case class EventPut(
    label: com.histmersion.api.catalogue.EventSummary.Label,
    kind: com.histmersion.api.catalogue.EventSummary.Kind = "event",
    timeSpan: TimeSpan = TimeSpan(),
    historicity: com.histmersion.api.catalogue.EventSummary.Historicity = "attested",
    geometry: Option[EventPut.Geometry] = None,
    placeId: Option[com.histmersion.api.catalogue.Place.Id] = None,
    parentId: Option[com.histmersion.api.catalogue.Event.Id] = None,
    description: Option[com.histmersion.api.catalogue.EventSummary.Description] = None,
    citekey: Option[com.histmersion.api.library.Citekey] = None,
    datings: List[EventDating] = Nil,
    edges: List[EventEdge] = Nil,
    participants: List[EventParticipant] = Nil,
    scale: Option[com.histmersion.api.catalogue.EventSummary.Scale] = None
) derives KindlingsSchema, com.histmersion.api.json.Schema
object EventPut:


  type Geometry = Geometry.T
  object Geometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given Codec.AsObject[EventPut] = StrictJson.codec(KindlingsCodecAsObject.derived[EventPut])
