package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

/** An edge parked until its target arrives (`unresolved_edges`): `dst` is a label, not a uid. */
final case class BundleUnresolvedEdge(srcUid: UUID, dst: String, kind: String) derives KindlingsCodecAsObject
