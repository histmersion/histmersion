package com.histmersion
package domain.automation.agents

import com.histmersion.api.automation.agents.Billing
import com.histmersion.api.automation.agents.Usage

import com.histmersion.server.agents.CliProcess

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.workflows.{ToolName, Workflows}
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.*
import sttp.shared.Identity

import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue, Semaphore}
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The run's remaining budget, checked by the sttp-ai loop before every iteration: once the spend recorded before this
  * step plus this step's own calls reaches the run's cap (cost or tokens), the loop is told to finish, and the engine
  * pauses the run. Our [[Agents.Billing]] prices the calls (an unpriced metered model never gets this far); a plan
  * model costs 0 USD, so only its tokens count.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final private[automation] class RunBudget(
    spentUsd: BigDecimal,
    spentTokens: RunBudget.SpentTokens,
    capUsd: BigDecimal,
    capTokens: RunBudget.CapTokens,
    billing: Billing,
    cacheReadWeight: Usage.CacheReadWeight = Usage.CacheReadWeight(1.0)
) extends AgentInterceptor[Identity]:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def decide(state: AgentRunState): LoopDecision =
    val use = LlmClient.usage(state.usage)
    val cost = billing.cost(use)
    if spentUsd + cost >= capUsd || spentTokens.value + use.budgeted(cacheReadWeight).value >= capTokens.value then
      LoopDecision.FinishNow(
        FinishReason.BudgetExceeded,
        sttp.ai.core.agent.interceptor.BudgetInterceptor.defaultInstruction
      )
    else LoopDecision.Continue

object RunBudget:
  type SpentTokens = SpentTokens.T
  object SpentTokens extends Newtype[Long, Pure]
  export AgentsControllers.BudgetTokens as CapTokens
