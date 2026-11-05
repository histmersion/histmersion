package com.histmersion.domain.catalogue.evidence

import com.augustnagro.magnum.DbCodec

/** A dated, placed finding near an event: how far, and the years between. */
final private[evidence] case class NearFindingRow(
    id: String,
    citekey: String,
    claim: String,
    km: Double,
    yearGap: Long,
    periodStart: Option[Long],
    periodEnd: Option[Long]
) derives DbCodec
