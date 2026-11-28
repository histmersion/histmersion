package com.histmersion
package domain.automation.agents

import com.histmersion.api.automation.agents.Billing

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.ModelChains.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A model chosen for one LLM call: the client, how it is paid for (a plan pool or a price), what it may spend before
  * an allowance covering it reaches its reserve (tokens, USD; None = not limited), and the chain's earlier models it
  * passed over, with why.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class Picked(
    client: LlmClient,
    billing: Billing,
    headroomTokens: Option[Picked.HeadroomTokens],
    headroomUsd: Option[BigDecimal],
    skipped: List[Picked.Skipped]
)

object Picked:
  export AllowanceGuard.HeadroomTokens
  type Skipped = Skipped.T
  object Skipped extends Newtype[String, Pure]
