package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.api.given

final case class BundleEventCheck(eventUid: UUID, checkedAt: String) derives KindlingsCodecAsObject
