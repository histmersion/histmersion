package com.histmersion.domain.catalogue.evidence

import com.augustnagro.magnum.DbCodec
import com.histmersion.util.database.given_DbCodec_List

/** A stored candidate link of an event to a source, its passages as JSON. */
final private[evidence] case class CandidateRow(
    eventId: String,
    citekey: String,
    score: Double,
    status: String,
    title: Option[String],
    findings: List[String],
    passages: String,
    reasons: List[String]
) derives DbCodec
