package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.api.{StrictJson, TimeSpan}
import com.histmersion.api.given

final case class EventPatch(
    label: Option[String] = None,
    kind: Option[String] = None,
    timeSpan: Option[TimeSpan] = None,
    historicity: Option[String] = None,
    geometry: Option[String] = None,
    placeId: Option[String] = None,
    parentId: Option[String] = None,
    description: Option[String] = None,
    citekey: Option[String] = None,
    datings: Option[List[EventDating]] = None,
    edges: Option[List[EventEdge]] = None,
    participants: Option[List[EventParticipant]] = None,
    scale: Option[String] = None,
    wikidata: Option[String] = None
) derives KindlingsSchema
object EventPatch:
  given Schema[EventPatch] = summon[KindlingsSchema[EventPatch]].schema

  given Codec.AsObject[EventPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[EventPatch])
