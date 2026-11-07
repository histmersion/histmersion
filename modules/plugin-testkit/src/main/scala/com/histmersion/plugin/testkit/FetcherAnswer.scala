package com.histmersion.plugin.testkit

import com.histmersion.plugin.*
import com.histmersion.pluginhost.FetchChain

/** One fetcher's answer when it had no copy: `refused` (a copy it may not take) or a miss. */
final case class FetcherAnswer(fetcher: FetchChain.Answer.Fetcher, refused: Boolean, why: FetchChain.Answer.Why)
