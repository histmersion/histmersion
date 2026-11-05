package com.histmersion.domain.library.sources

import com.histmersion.util.database.given

import com.augustnagro.magnum.*
import com.histmersion.domain.library.Source

/** A source's row as a write path reads it back (Magnum maps a SELECT's columns positionally). */
final private[sources] case class FullSourceRow(
    citekey: String,
    title: String,
    authors: List[String],
    doi: Option[String],
    year: Option[Int],
    venue: Option[String],
    status: Source.Status,
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
