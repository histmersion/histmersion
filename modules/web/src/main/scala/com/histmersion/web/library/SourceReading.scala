package com.histmersion.web.library

/** What the reading pane reads of a source beyond its row: its record with its findings, and its place in the citation
  * graph.
  */
import com.histmersion.api.library.Source
final case class SourceReading(source: Source.WithFindings, counts: CitationCounts)
