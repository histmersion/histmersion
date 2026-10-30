package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.values.BundleKind

final case class BundleTombstone(uid: UUID, kind: BundleKind, origin: UUID, deletedAt: String)
    derives KindlingsCodecAsObject
