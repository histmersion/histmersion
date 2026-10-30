package com.histmersion.api

/** What `histmersion ingest` uploads about the file, beside the bytes themselves. Query parameters rather than a
  * multipart part list: tapir's netty server has no multipart request-body support (it throws
  * `UnsupportedOperationException`), so the upload is a raw body plus typed parameters.
  */
final case class IngestParams(
    citekey: String,
    filename: Option[String] = None,
    quality: Boolean = false,
    force: Boolean = false,
    title: Option[String] = None,
    authors: List[String] = Nil,
    year: Option[Int] = None,
    doi: Option[String] = None,
    provenance: Option[String] = None,
    /** The acquisition item this upload fulfils. The OCR job ticks it off (`ingested as [[citekey]]`) when, and only
      * when, it succeeds; a failed job leaves it open.
      */
    acquireItem: Option[Int] = None
)
