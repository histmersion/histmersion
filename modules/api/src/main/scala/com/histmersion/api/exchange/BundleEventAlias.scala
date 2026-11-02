package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.api.given

final case class BundleEventAlias(alias: String, eventUid: UUID, createdAt: String) derives KindlingsCodecAsObject
