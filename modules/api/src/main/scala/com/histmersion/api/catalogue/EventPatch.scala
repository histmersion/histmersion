package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.{StrictJson, TimeSpan}
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class EventPatch(
    label: Option[com.histmersion.api.catalogue.EventSummary.Label] = None,
    kind: Option[com.histmersion.api.catalogue.EventSummary.Kind] = None,
    timeSpan: Option[TimeSpan] = None,
    historicity: Option[com.histmersion.api.catalogue.EventSummary.Historicity] = None,
    geometry: Option[EventPatch.Geometry] = None,
    placeId: Option[com.histmersion.api.catalogue.Place.Id] = None,
    parentId: Option[com.histmersion.api.catalogue.Event.Id] = None,
    description: Option[com.histmersion.api.catalogue.EventSummary.Description] = None,
    citekey: Option[com.histmersion.api.library.Citekey] = None,
    datings: Option[List[EventDating]] = None,
    edges: Option[List[EventEdge]] = None,
    participants: Option[List[EventParticipant]] = None,
    scale: Option[com.histmersion.api.catalogue.EventSummary.Scale] = None,
    wikidata: Option[EventPatch.Wikidata] = None
) derives KindlingsSchema, com.histmersion.api.json.Schema
object EventPatch:


  type Geometry = Geometry.T
  object Geometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wikidata = Wikidata.T
  object Wikidata extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given Codec.AsObject[EventPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[EventPatch])
