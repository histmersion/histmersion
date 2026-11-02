package com.histmersion.domain.catalogue.evidence

import com.histmersion.util.database.given
import com.augustnagro.magnum.DbCodec
import java.util.UUID

/** A source that possibly discusses an event, as its bundle line reads it. */
final private[evidence] case class BCandidateRow(
    eventUid: UUID,
    sourceUid: UUID,
    citekey: String,
    score: Double,
    status: String,
    findings: List[String],
    passages: String,
    reasons: List[String],
    computedAt: String
) derives DbCodec
