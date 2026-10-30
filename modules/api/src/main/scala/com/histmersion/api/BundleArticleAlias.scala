package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

final case class BundleArticleAlias(alias: String, articleUid: UUID, createdAt: String) derives KindlingsCodecAsObject
