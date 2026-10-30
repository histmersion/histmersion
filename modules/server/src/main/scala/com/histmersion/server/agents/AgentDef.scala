package com.histmersion.server.agents

import scala.annotation.tailrec
import com.histmersion.api.Agents.Role

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** One canonical agent definition, `agents/<name>.md`: a runtime-neutral manifest in the YAML header and the prompt
  * body, which speaks of tools by `{{tool:<name>}}` placeholder.
  */
final case class AgentDef(
    name: String,
    description: String,
    role: Role,
    skills: List[String],
    include: List[String],
    tools: List[ToolName],
    web: WebAccess,
    writes: Writes,
    maxTurns: Int,
    answer: Option[String],
    runtimes: Set[AgentRuntime],
    interactive: Option[InteractiveSpec],
    body: String
):
  def runsOn(r: AgentRuntime): Boolean = runtimes.contains(r)
