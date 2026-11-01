package com.histmersion.domain.library

import com.histmersion.domain.library.citations.CitationService
import com.histmersion.domain.library.sources.SourceService

/** The library's services of one test, sharing their records. */
trait LibraryStores:
  def sources: SourceService
  def citations: CitationService
