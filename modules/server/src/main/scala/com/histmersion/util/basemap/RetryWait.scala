package com.histmersion.util.basemap

/** How long to wait before asking a provider again, and why (for the log). */
final case class RetryWait(ms: Long, why: String)
