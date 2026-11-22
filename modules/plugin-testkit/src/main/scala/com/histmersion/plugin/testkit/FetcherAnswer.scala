package com.histmersion
package plugin.testkit

import com.histmersion.util.problem.Problem
import com.histmersion.plugin.*
import com.histmersion.pluginhost.FetchChain

/** One fetcher's answer when it had no copy: `said` is the absence or refusal it gave, as the server stores it with the
  * acquisition attempt and decides from it who acts next.
  */
final case class FetcherAnswer(fetcher: FetchChain.Answer.Fetcher, said: Problem.NotFound.Said, why: Problem):
  def refused: Boolean = said match
    case _: Problem.NotFound.Said.Refused => true
    case _: Problem.NotFound.Said.Miss    => false
