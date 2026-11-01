package com.histmersion.domain.library.sources

import com.histmersion.db.given
import com.histmersion.values.SourceStatus

import com.augustnagro.magnum.*

/** A source's row as a write path reads it back (Magnum maps a SELECT's columns positionally). */
final private[sources] case class FullSourceRow(
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
    periodStart: Option[Long],
    periodEnd: Option[Long],
    summary: Option[String],
    citedByCount: Option[Int],
    influentialCitations: Option[Int],
    provenance: Option[String],
    calibreId: Option[String],
    ocrMarkdown: Option[String],
    notes: Option[String],
    updatedAt: String
) derives DbCodec
