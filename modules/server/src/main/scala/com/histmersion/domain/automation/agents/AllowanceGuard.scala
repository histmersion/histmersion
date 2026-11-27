package com.histmersion
package domain.automation.agents

import com.histmersion.domain.automation.approvals.Decided
import com.histmersion.domain.automation.managers.Zero
import com.histmersion.server.agents.Codex

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.*
import sttp.shared.Identity

import java.util.concurrent.{ConcurrentHashMap, Semaphore}
import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** A model's allowance, inside one step: once the step's own calls have spent what the model had left above its reserve
  * (tokens or USD, as [[ModelPicker]] measured at the pick), the loop is told to finish, and the engine hands the step
  * to the chain's next model. At most one call overshoots.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final private[automation] class AllowanceGuard(
    headroomTokens: Option[AllowanceGuard.HeadroomTokens],
    headroomUsd: Option[BigDecimal],
    billing: Billing
) extends AgentInterceptor[Identity]:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def decide(state: AgentRunState): LoopDecision =
    val use = LlmClient.usage(state.usage)
    val overTokens = headroomTokens.exists(h => use.total.value >= h.value)
    val overUsd = headroomUsd.exists(h => billing.cost(use) >= h)
    if overTokens || overUsd then
      LoopDecision.FinishNow(
        FinishReason.Custom(AllowanceGuard.Reason),
        sttp.ai.core.agent.interceptor.BudgetInterceptor.defaultInstruction
      )
    else LoopDecision.Continue

private[automation] object AllowanceGuard:
  type HeadroomTokens = HeadroomTokens.T
  object HeadroomTokens extends Newtype[Long, Pure]

  val Reason = "allowance"
