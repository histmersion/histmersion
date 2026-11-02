package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.api.given

final case class BundleEventEdge(
    dstUid: UUID,
    kind: String,
    citekey: String,
    sourceUid: Option[UUID],
    note: Option[String]
) derives KindlingsCodecAsObject
