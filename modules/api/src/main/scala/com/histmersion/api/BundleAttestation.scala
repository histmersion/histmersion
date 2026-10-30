package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleAttestation(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    targetType: String,
    targetId: String,
    targetUid: Option[UUID],
    quote: Option[String],
    prefix: Option[String],
    suffix: Option[String],
    charStart: Option[Int],
    charEnd: Option[Int],
    chunkOrd: Option[Int],
    page: Option[String],
    canvas: Option[String],
    xywh: Option[String],
    ctsUrn: Option[String],
    note: Option[String],
    createdAt: String,
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject
