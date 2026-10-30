package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleAlias(alias: String, sourceUid: UUID, createdAt: String) derives KindlingsCodecAsObject
