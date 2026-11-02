package com.histmersion.api.library
import com.histmersion.api.given
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
