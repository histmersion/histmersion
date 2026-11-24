package com.histmersion
package domain.automation.agents

import sttp.ai.core.agent.*
import sttp.shared.Identity

import scala.concurrent.duration.*
import com.histmersion.lint.{LintRule, tolerated}

/** The input of a step's FIRST model call — input + cache read + cache write tokens: the fixed part every turn re-sends
  * (the system prompt, the tool definitions) plus the step's own prompt. Measured per step attempt so a change to that
  * fixed part shows (`agent show`: "first call N tok").
  *
  * An HTTP provider's tool loop passes [[aroundLlmCall]] once per model call, so the first response's usage is the
  * first call's. A command-line provider runs its whole loop as ONE intercepted call whose usage is the whole loop's:
  * it [[FirstCallProbe.report]]s what its own output says (None when it cannot tell) before that call returns, and the
  * total is never taken for the first call.
  */
final private[histmersion] class FirstCallProbe extends AgentInterceptor[Identity]:
  // set once per step attempt: by the first response, or by a CLI client's report (which wins)
  private val state =
    java.util.concurrent.atomic.AtomicReference[Option[Option[AgentRepo.StepRecord.FirstCallTokens]]](None)

  def tokens: Option[AgentRepo.StepRecord.FirstCallTokens] = state.get().flatten

  def report(tokens: Option[AgentRepo.StepRecord.FirstCallTokens]): Unit = state.set(Some(tokens))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def aroundLlmCall(ctx: LlmCallContext)(next: => Identity[AgentResponse]): Identity[AgentResponse] =
    val r = next
    val _ = state.compareAndSet(
      None,
      Some(r.usage.map(_.inputTokens.value).filter(_ > 0).map(AgentRepo.StepRecord.FirstCallTokens(_)))
    )
    r

private[histmersion] object FirstCallProbe:
  /** A command-line client's reading of its first model call, to every probe among `interceptors`. */

  def report(
      interceptors: List[AgentInterceptor[Identity]],
      tokens: Option[AgentRepo.StepRecord.FirstCallTokens]
  ): Unit =
    interceptors.foreach { case p: FirstCallProbe => p.report(tokens); case _ => () }
