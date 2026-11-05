package com.histmersion.domain.automation.agents

import com.histmersion.domain.automation.workflows.ToolName
import io.netty.handler.codec.http.*

/** What one run token allows: the agent definition a step runs as, and so exactly its tools; the run and step it
  * belongs to; when it expires (epoch ms).
  */
final case class RunGrant(
    token: String,
    agent: String,
    tools: List[ToolName],
    run: Option[String],
    step: Option[Int],
    expiresAt: Long
)
