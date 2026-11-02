package com.histmersion.domain.library.sources

import com.histmersion.util.database.given
import com.histmersion.values.SourceStatus
import com.augustnagro.magnum.*
import java.util.UUID

/** A source as the bundle reads it: its columns, its body's and its original's digests and its ISBNs. */
final private[sources] case class BSourceRow(
    uid: UUID,
    origin: UUID,
    citekey: String,
    title: String,
    authors: List[String],
    doi: Option[String],
    year: Option[Int],
    venue: Option[String],
    status: SourceStatus,
    topics: List[String],
    regions: List[String],
    tags: List[String],
    pNull: Boolean,
    pLo: Option[Long],
    pHi: Option[Long],
    summary: Option[String],
    notes: Option[String],
    provenance: Option[String],
    citedBy: Option[Int],
    influential: Option[Int],
    addedDate: String,
    bodySha: Option[String],
    bodyBytes: Option[Long],
    originalSha: Option[String],
    updatedAt: String,
    isbns: List[String]
) derives DbCodec
