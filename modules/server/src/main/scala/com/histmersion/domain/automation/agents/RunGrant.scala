package com.histmersion.domain.automation.agents

import com.histmersion.domain.automation.workflows.ToolName
import io.netty.handler.codec.http.*

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
  object Token extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Agent = Agent.T
  object Agent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Run = Run.T
  object Run extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Step = Step.T
  object Step extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExpiresAt = ExpiresAt.T
  object ExpiresAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
