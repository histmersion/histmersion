package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.api.given

final case class BundleGoalLink(
    kind: String,
    ref: String,
    refUid: Option[UUID],
    note: Option[String],
    createdAt: String
) derives KindlingsCodecAsObject
