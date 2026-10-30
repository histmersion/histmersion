package com.histmersion.server.acquire

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** No citation or author alerts (a test, or a server built without them). */
object NoAlerts extends CitationAlerts:
  private val off = Left("the citation alerts are not connected")
  def citing(doi: String, since: java.time.LocalDate, limit: Int) = off
  def authors(doi: String) = off
  def byAuthor(authorId: String, since: java.time.LocalDate, limit: Int) = off
