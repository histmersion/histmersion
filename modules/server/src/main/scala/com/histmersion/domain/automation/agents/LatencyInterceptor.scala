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
import com.histmersion.util.time.Timer
import com.histmersion.lint.{LintRule, tolerated}

/** Times each LLM call of a step and holds one of the server-wide `max-llm-calls-in-flight` slots while it runs, so
  * parallel steps and concurrent runs together never exceed the cap.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final private[automation] class LatencyInterceptor(slots: Semaphore, beat: () => Unit = () => ())(using timer: Timer)
    extends AgentInterceptor[Identity]:
  /** Per-call latencies, in call order. A `var` local to one step's interceptor instance. */
  @volatile var millis: Vector[AgentRepo.MessageRecord.LatencyMs] = Vector.empty
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def aroundLlmCall(ctx: LlmCallContext)(next: => Identity[AgentResponse]): Identity[AgentResponse] =
    // waiting for a slot behind other runs' calls is not a stall: beat while waiting
    while !slots.tryAcquire(Engine.BeatWhileWaitingSeconds, java.util.concurrent.TimeUnit.SECONDS) do beat()
    beat()
    val t0 = timer.start()
    try next
    finally
      millis = millis :+ AgentRepo.MessageRecord.LatencyMs(t0.elapsed.toMillis.toInt)
      slots.release()
      beat()
