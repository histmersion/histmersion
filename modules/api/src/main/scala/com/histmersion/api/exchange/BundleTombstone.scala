package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.api.given

final case class BundleTombstone(
    uid: UUID,
    kind: com.histmersion.util.data.exchange.Bundle.Kind,
    origin: UUID,
    deletedAt: String
) derives KindlingsCodecAsObject
