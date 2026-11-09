package com.histmersion
package plugin.testkit

import com.histmersion.util.problem.Problem
import com.histmersion.plugin.*
import com.histmersion.pluginhost.FetchChain

/** One fetcher's answer when it had no copy: `refused` (a copy it may not take) or a miss. */
final case class FetcherAnswer(fetcher: FetchChain.Answer.Fetcher, refused: Boolean, why: Problem)
