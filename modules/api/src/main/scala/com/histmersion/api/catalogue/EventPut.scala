package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.{StrictJson, TimeSpan}
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
    scale: Option[EventSummary.Scale] = None,
    createdBy: Option[Creation.By] = None,
    via: Option[Creation.Via] = None
) extends Creation.Requested derives StrictJson, KindlingsSchema, api.json.Schema
object EventPut:

  export EventPatch.Geometry
