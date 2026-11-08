package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.{StrictJson, TimeSpan}
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

final case class EventPatch(
    label: Option[EventSummary.Label] = None,
    kind: Option[EventSummary.Kind] = None,
    timeSpan: Option[TimeSpan] = None,
    historicity: Option[EventSummary.Historicity] = None,
    geometry: Option[EventPatch.Geometry] = None,
    placeId: Option[Place.Id] = None,
    parentId: Option[Event.Id] = None,
    description: Option[EventSummary.Description] = None,
    citekey: Option[Citekey] = None,
    datings: Option[List[EventDating]] = None,
    edges: Option[List[EventEdge]] = None,
    participants: Option[List[EventParticipant]] = None,
    scale: Option[EventSummary.Scale] = None,
    wikidata: Option[EventPatch.Wikidata] = None
) derives KindlingsSchema, com.histmersion.api.json.Schema
object EventPatch:


  type Geometry = Geometry.T
  object Geometry extends RefinedType[String, Pure]
  type Wikidata = Wikidata.T
  object Wikidata extends RefinedType[String, Pure]

  given Codec.AsObject[EventPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[EventPatch])
