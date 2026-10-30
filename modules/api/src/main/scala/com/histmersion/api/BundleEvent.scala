package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleEvent(
    uid: UUID,
    origin: UUID,
    id: String,
    label: String,
    kind: String,
    notBefore: Option[Long],
    beginBy: Option[Long],
    endFrom: Option[Long],
    notAfter: Option[Long],
    historicity: String,
    geom: Option[String],
    /** A gazetteer id: places are reference data present on both sides (see [[BundleReference]]). */
    placeId: Option[String],
    parentUid: Option[UUID],
    description: Option[String],
    citekey: Option[String],
    sourceUid: Option[UUID],
    datings: List[BundleEventDating],
    /** The OUTGOING edges (`src_id` = this event). */
    edges: List[BundleEventEdge],
    participants: List[BundleEventParticipant],
    updatedAt: String,
    rev: String = "",
    /** The STATED scale (V078), `local` | `regional`; absent = derived — and absent from the JSON, so the rev of an
      * event that states none is what it was before the column.
      */
    scale: Option[String] = None,
    /** The catalogue an import created it from (V085: `gvp`, `noaa`, `wikidata`), its other names, its Wikidata item
      * (V082), and the dating its catalogue STATES (V088: the year, its ±, the method). Each is absent when empty, so
      * the rev of an event compiled from sources is what it was before the fields travelled (#1647).
      */
    catalogue: Option[String] = None,
    names: Option[List[String]] = None,
    wikidata: Option[String] = None,
    central: Option[Long] = None,
    plusMinus: Option[Long] = None,
    datingMethod: Option[String] = None
) derives KindlingsCodecAsObject
