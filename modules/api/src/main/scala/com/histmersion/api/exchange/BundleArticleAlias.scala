package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.api.given

final case class BundleArticleAlias(alias: String, articleUid: UUID, createdAt: String) derives KindlingsCodecAsObject
