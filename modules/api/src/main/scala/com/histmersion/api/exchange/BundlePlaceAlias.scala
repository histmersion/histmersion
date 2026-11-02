package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.api.given

final case class BundlePlaceAlias(alias: String, placeUid: UUID, createdAt: String) derives KindlingsCodecAsObject
