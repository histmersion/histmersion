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
    /** The sha256 of the UTF-8 OCR body, a blob of the bundle. */
    bodySha256: Option[String],
    /** `sources.sha256`: the digest of the uploaded original. */
    originalSha256: Option[String],
    /** The extension the server stored the original under — present iff the original is a blob of the bundle (a source
      * ingested elsewhere has a digest but no stored file).
      */
    originalExt: Option[String],
    updatedAt: String,
    rev: String = "",
    /** ISBN-13s recorded for the source (`source_identifiers`); absent when there are none, so a line written before
      * the field existed keeps its rev. A strong key of the matcher (M3).
      */
    isbns: Option[List[String]] = None
) derives KindlingsCodecAsObject
