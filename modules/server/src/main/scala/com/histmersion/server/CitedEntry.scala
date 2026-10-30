package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.values.SourceStatus

/** One line of an article's "Sources cited" footer; `number` is its citation number in the prose (first appearance),
  * `None` for a source cited only outside it (e.g. under "Not used").
  */
final case class CitedEntry(
    number: Option[Int],
    citekey: String,
    doi: Option[String],
    authors: List[String],
    year: Option[Int],
    title: Option[String],
    status: Option[SourceStatus]
)
