package com.histmersion.domain.automation.agents

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.ModelChains.*

/** A model chosen for one LLM call: the client, how it is paid for (a plan pool or a price), what it may spend before
  * an allowance covering it reaches its reserve (tokens, USD; None = not limited), and the chain's earlier models it
  * passed over, with why.
  */
final case class Picked(
    client: LlmClient,
    billing: Billing,
    headroomTokens: Option[Long],
    headroomUsd: Option[BigDecimal],
    skipped: List[String]
)
