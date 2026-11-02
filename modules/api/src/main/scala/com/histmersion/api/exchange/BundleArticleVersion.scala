package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.api.given

final case class BundleArticleVersion(
    uid: UUID,
    origin: UUID,
    articleUid: UUID,
    bodySha256: String,
    summary: Option[String],
    versionOf: String,
    updatedAt: String,
    rev: String = ""
) derives KindlingsCodecAsObject
