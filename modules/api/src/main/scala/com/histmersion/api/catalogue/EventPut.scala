package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.{StrictJson, TimeSpan}
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey

final case class EventPut(
    label: EventSummary.Label,
    kind: EventSummary.Kind = EventSummary.Kind("event"),
    timeSpan: TimeSpan = TimeSpan(),
    historicity: EventSummary.Historicity = EventSummary.Historicity("attested"),
    geometry: Option[EventPut.Geometry] = None,
    placeId: Option[Place.Id] = None,
    parentId: Option[Event.Id] = None,
    description: Option[EventSummary.Description] = None,
    citekey: Option[Citekey] = None,
    datings: List[EventDating] = Nil,
    edges: List[EventEdge] = Nil,
    participants: List[EventParticipant] = Nil,
    scale: Option[EventSummary.Scale] = None
) derives StrictJson,
      KindlingsSchema,
      api.json.Schema
object EventPut:

  export EventPatch.Geometry
