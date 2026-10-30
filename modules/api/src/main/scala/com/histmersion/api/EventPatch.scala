package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec

/** Selective patch for one event. */
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
    /** `local` | `regional` states the scale; `auto` clears it (derived again). */
    scale: Option[String] = None,
    /** The Wikidata item the event is (`Q…`); `""` clears it. */
    wikidata: Option[String] = None
) derives KindlingsSchema
object EventPatch:
  given Schema[EventPatch] = summon[KindlingsSchema[EventPatch]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[EventPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[EventPatch])
