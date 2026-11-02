package com.histmersion.domain.library.ingest

import com.histmersion.api.Edge

/** The metadata an ingest carries, whatever the acquisition channel was. */
final case class IngestMeta(
    title: String,
    authors: List[String] = Nil,
    doi: Option[String] = None,
    year: Option[Int] = None,
    venue: Option[String] = None,
    summary: Option[String] = None,
    provenance: String = "oa",
    /** `references` (and other typed) edges discovered while acquiring. */
    edges: List[Edge] = Nil,
    citedByCount: Option[Int] = None,
    influentialCitations: Option[Int] = None
)
