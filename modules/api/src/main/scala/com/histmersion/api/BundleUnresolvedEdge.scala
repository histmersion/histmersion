package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleUnresolvedEdge(srcUid: UUID, dst: String, kind: String) derives KindlingsCodecAsObject
