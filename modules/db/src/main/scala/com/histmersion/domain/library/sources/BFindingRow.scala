package com.histmersion.domain.library.sources

import com.histmersion.domain.library.findings.FindingColumns.given

import com.histmersion.util.database.given
import com.histmersion.domain.library.Confidence
import com.augustnagro.magnum.*
import java.util.UUID

/** A finding as the bundle reads it, with its source's uid. */
final private[sources] case class BFindingRow(
    uid: UUID,
    origin: UUID,
    sourceUid: UUID,
    id: String,
    claim: String,
    region: Option[String],
    lon: Option[Double],
    lat: Option[Double],
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long],
    periodName: Option[String],
    tags: List[String],
    confidence: Option[Confidence],
    significance: Option[String],
    updatedAt: String
) derives DbCodec
