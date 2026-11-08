package com.histmersion
package domain.automation.agents

import sttp.ai.core.agent.*
import sttp.shared.Identity

import scala.concurrent.duration.*
import io.github.iltotore.iron.constraint.any.Pure

/** One LLM step's request, provider-neutral: a system prompt, ONE user message (untrusted data goes here, delimited,
  * never into the system prompt), the tools the step is allowed (its allowlist — sttp-ai's loop answers "Tool not
  * found" for anything else), the typed answer, and the loop limits. `interceptors` see every LLM and tool call
  * (tracing, the run's budget).
  */
final case class LlmCall[Out](
    system: LlmCall.System,
    user: LlmCall.User,
    tools: List[AgentTool[Identity, ?]],
    schema: ResponseSchema[Out],
    maxIterations: LlmCall.MaxIterations,
    maxTokens: LlmCall.MaxTokens,
    interceptors: List[AgentInterceptor[Identity]] = Nil,
    /** The canonical definition this call runs as: a CLI runtime launches it by name (the generated agent carries the
      * same prompt, rendered for that runtime), and a run token is bound to it.
      */
    agent: Option[AgentDef] = None,
    /** The run and step the call belongs to (None: a probe outside any run). */
    scope: Option[RunScope] = None
)

object LlmCall:
  type System = System.T
  object System extends Newtype[String, Pure]
  type User = User.T
  object User extends Newtype[String, Pure]
  type MaxIterations = MaxIterations.T
  object MaxIterations extends Newtype[Int, Pure]
  type MaxTokens = MaxTokens.T
  object MaxTokens extends Newtype[Int, Pure]
