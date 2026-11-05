package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

/** One fetcher's answer when it had no copy: `refused` (a copy it may not take) or a miss. */
final case class FetcherAnswer(fetcher: String, refused: Boolean, why: String)
