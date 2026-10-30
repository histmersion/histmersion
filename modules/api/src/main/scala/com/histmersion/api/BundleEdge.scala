package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleEdge(srcUid: UUID, dstUid: UUID, kind: String) derives KindlingsCodecAsObject
