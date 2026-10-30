package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleEventEdge(
    dstUid: UUID,
    kind: String,
    citekey: String,
    sourceUid: Option[UUID],
    note: Option[String]
) derives KindlingsCodecAsObject
