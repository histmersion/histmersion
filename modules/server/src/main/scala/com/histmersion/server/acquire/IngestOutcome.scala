package com.histmersion.server.acquire

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

/** @param created false when the citekey already existed and was enriched rather than replaced */
final case class IngestOutcome(citekey: String, created: Boolean, jobId: Option[String], detail: String)
