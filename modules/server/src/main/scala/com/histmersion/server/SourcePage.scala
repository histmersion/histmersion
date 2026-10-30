package com.histmersion.server

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** Everything a `/source/{citekey}` page shows, typed. Built by [[PageBuilder]], rendered by [[Pages.source]] —
  * Markdown bodies stay Markdown until rendering, so the page model is testable without flexmark in the way.
  */
final case class SourcePage(
    ref: SourceRef,
    venue: Option[String],
    periodStart: Option[Long],
    periodEnd: Option[Long],
    topics: List[String],
    regions: List[String],
    tags: List[String],
    summary: Option[String],
    notes: Option[String],
    findings: List[FindingRow],
    cites: List[Citation],
    citedBy: List[Citation],
    fullText: FullText,
    query: Option[String] = None,
    /** The summary's and notes' formatting problems — the page says so above them. */
    formatting: List[FormatIssue] = Nil
)
