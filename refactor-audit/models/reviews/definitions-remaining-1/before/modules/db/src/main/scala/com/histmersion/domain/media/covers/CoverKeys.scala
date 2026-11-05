package com.histmersion.domain.media.covers

/** A book's identifiers the Open Library Covers API answers by. */
final case class CoverKeys(citekey: String, isbn: Option[String], oclc: Option[String])
