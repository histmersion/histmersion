package com.histmersion
package domain.automation.agents

import com.histmersion.domain.automation.workflows.ToolName
import io.netty.handler.codec.http.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What one run token allows: the agent definition a step runs as, and so exactly its tools; the run and step it
  * belongs to; when it expires (epoch ms).
  */
final case class RunGrant(
    token: RunGrant.Token,
    agent: RunGrant.Agent,
    tools: List[ToolName],
    run: Option[RunGrant.Run],
    step: Option[RunGrant.Step],
    expiresAt: RunGrant.ExpiresAt
)

object RunGrant:
  type Token = Token.T
  object Token extends RefinedType[String, Pure]
  type Agent = Agent.T
  object Agent extends RefinedType[String, Pure]
  type Run = Run.T
  object Run extends RefinedType[String, Pure]
  type Step = Step.T
  object Step extends RefinedType[Int, Pure]
  type ExpiresAt = ExpiresAt.T
  object ExpiresAt extends RefinedType[Long, Pure]
