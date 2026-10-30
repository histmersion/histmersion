package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleEventAlias(alias: String, eventUid: UUID, createdAt: String) derives KindlingsCodecAsObject
