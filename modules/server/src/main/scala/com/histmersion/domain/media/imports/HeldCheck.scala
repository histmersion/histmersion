package com.histmersion.domain.media.imports

import java.time.Instant

/** That a provider was asked about a place, an event or a source, as the in-memory store holds it (`media_checks`). */
final case class HeldCheck(
    provider: String,
    targetType: String,
    targetId: String,
    found: Int,
    qid: Option[String],
    checkedAt: Instant
)
