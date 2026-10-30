package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

/** A source → source edge (`edges`). The whole out-set of a source travels with it. */
final case class BundleEdge(srcUid: UUID, dstUid: UUID, kind: String) derives KindlingsCodecAsObject
