package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundlePlaceAlias(alias: String, placeUid: UUID, createdAt: String) derives KindlingsCodecAsObject
