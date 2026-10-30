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
    placeId: Option[String],
    parentUid: Option[UUID],
    description: Option[String],
    citekey: Option[String],
    sourceUid: Option[UUID],
    datings: List[BundleEventDating],
    edges: List[BundleEventEdge],
    participants: List[BundleEventParticipant],
    updatedAt: String,
    rev: String = "",
    /** Absent from the JSON when empty, so the rev of an event that states none stays what older bundles computed. */
    scale: Option[String] = None,
    /** Each absent from the JSON when empty, so the rev of an event compiled from sources stays what older bundles
      * computed.
      */
    catalogue: Option[String] = None,
    names: Option[List[String]] = None,
    wikidata: Option[String] = None,
    central: Option[Long] = None,
    plusMinus: Option[Long] = None,
    datingMethod: Option[String] = None
) derives KindlingsCodecAsObject
