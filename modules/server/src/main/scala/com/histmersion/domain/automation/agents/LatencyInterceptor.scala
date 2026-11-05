package com.histmersion.domain.automation.agents

import com.histmersion.domain.automation.approvals.Decided
import com.histmersion.domain.automation.managers.Zero
import com.histmersion.server.agents.Codex

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.api.VocabularyCodecs.given
import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.ai.core.agent.*
import sttp.shared.Identity

import java.util.concurrent.{ConcurrentHashMap, Semaphore}
import scala.jdk.CollectionConverters.*

/** Times each LLM call of a step and holds one of the server-wide `max-llm-calls-in-flight` slots while it runs, so
  * parallel steps and concurrent runs together never exceed the cap.
  */
final private[automation] class LatencyInterceptor(slots: Semaphore, beat: () => Unit = () => ())(using timer: com.histmersion.util.time.Timer)
    extends AgentInterceptor[Identity]:
  /** Per-call latencies, in call order. A `var` local to one step's interceptor instance. */
  @volatile var millis: Vector[Int] = Vector.empty
  override def aroundLlmCall(ctx: LlmCallContext)(next: => Identity[AgentResponse]): Identity[AgentResponse] =
    // waiting for a slot behind other runs' calls is not a stall: beat while waiting
    while !slots.tryAcquire(Engine.BeatWhileWaitingSeconds, java.util.concurrent.TimeUnit.SECONDS) do beat()
    beat()
    val t0 = timer.start()
    try next
    finally
      millis = millis :+ t0.elapsed.toMillis.toInt
      slots.release()
      beat()
