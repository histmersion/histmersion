package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

/** An old id of a place (`place_aliases`, V055) and the place it names now. */
final case class BundlePlaceAlias(alias: String, placeUid: UUID, createdAt: String) derives KindlingsCodecAsObject
