package com.histmersion.plugins.calibre


/** What `calibre push` sends into Calibre for one source. */
final case class CalibrePush(
    title: String,
    authors: List[String],
    doi: Option[String] = None,
    isbn: Option[String] = None,
    year: Option[Int] = None,
    publisher: Option[String] = None,
    summary: Option[String] = None,
    tags: List[String] = Nil
)
