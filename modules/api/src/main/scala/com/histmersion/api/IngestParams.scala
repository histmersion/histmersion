package com.histmersion.api

/** Query parameters, not a multipart part list: tapir's netty server has no multipart request-body support (it throws
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
    acquireItem: Option[Int] = None
)
