package com.histmersion.web.library

import com.histmersion.api.SourceWithFindings

/** What the reading pane reads of a source beyond its row: its record with its findings, and its place in
  * the citation graph. */
final case class SourceReading(source: SourceWithFindings, counts: CitationCounts)
