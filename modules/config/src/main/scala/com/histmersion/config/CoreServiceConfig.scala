package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader

/** CORE (api.core.ac.uk): its endpoint, the plain key (`api-key`; the `oa-core` plugin's secret reference wins over
  * it), and the budget both CORE uses share — the acquisition chain's `core` step and the new-source search's `core`
  * index. A personal key allows 1,000 tokens a day and 25 a minute; a simple query costs 1 token.
  *
  * @param perMinute
  *   calls in any 60 s, at most
  * @param dailyTokens
  *   tokens a UTC day, at most, both uses together
  * @param searchDailyTokens
  *   of those, what the searches may spend: the rest stays for PDF lookups
  * @param lookupCost
  *   tokens one DOI lookup costs (CORE may change it)
  * @param searchCost
  *   tokens one search costs
  * @param exhaustedPauseSeconds
  *   the pause after a 429 or `X-RateLimit-Remaining: 0` without a retry time
  * @param maxWaitSeconds
  *   how long a call waits for its slot under `perMinute`; longer, and it is not made (the chain goes on, the sweep
  *   leaves CORE out)
  */
final case class CoreServiceConfig(
    url: ServiceUrl,
    apiKey: ApiKey,
    perMinute: ItemCount,
    dailyTokens: TokenCount,
    searchDailyTokens: TokenCount,
    lookupCost: TokenCount,
    searchCost: TokenCount,
    exhaustedPauseSeconds: Seconds,
    maxWaitSeconds: Seconds
) derives ConfigReader:
  def key: Option[ApiKey] = Option(apiKey.value: String).map(_.trim).filter(_.nonEmpty).map(ApiKey(_))

