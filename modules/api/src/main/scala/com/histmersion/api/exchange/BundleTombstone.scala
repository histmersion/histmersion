package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.values.BundleKind
import com.histmersion.api.given

final case class BundleTombstone(uid: UUID, kind: BundleKind, origin: UUID, deletedAt: String)
    derives KindlingsCodecAsObject
