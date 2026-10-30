package com.histmersion.api

import com.histmersion.values.SourceStatus

/** One paper/book. `ocrMarkdown` (the full converted body) is the store-of-record content that the server serialises
  * back to the LLM as Markdown; the original binary stays in Calibre.
  */
final case class Source(
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
    period: Option[Period],
    summary: Option[String],
    citedByCount: Option[Int],
    influentialCitations: Option[Int],
    provenance: Option[String],
    calibreId: Option[String],
    ocrMarkdown: Option[String] = None,
    findings: List[Finding] = Nil,
    edges: List[Edge] = Nil,
    /** Free Markdown the agent compiles ABOUT this source (the body of `wiki/sources/<ck>.md`). */
    notes: Option[String] = None,
    /** Opaque last-write token (`updated_at`), set by the store. `patch_source`'s `ifUpdatedAt` precondition compares
      * against it; `None` when the value did not come from the database.
      */
    updatedAt: Option[String] = None
)
