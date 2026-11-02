package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.api.given

final case class BundleUnresolvedEdge(srcUid: UUID, dst: String, kind: String) derives KindlingsCodecAsObject
