package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID
import com.histmersion.values.SourceStatus

final case class BundleSource(
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
    period: Option[BundleRange],
    summary: Option[String],
    notes: Option[String],
    provenance: Option[String],
    citedByCount: Option[Int],
    influentialCitations: Option[Int],
    addedDate: String,
    bodySha256: Option[String],
    originalSha256: Option[String],
    originalExt: Option[String],
    updatedAt: String,
    rev: String = "",
    /** Absent when there are none, so a line written before the field existed keeps its rev. */
    isbns: Option[List[String]] = None
) derives KindlingsCodecAsObject
