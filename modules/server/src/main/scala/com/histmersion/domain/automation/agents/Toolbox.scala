package com.histmersion.domain.automation.agents

import com.histmersion.domain.automation.workflows.{ToolContribution, ToolName}
import sttp.ai.core.agent.AgentTool
import sttp.shared.Identity

/** The engine's toolbox: the tools the domains contribute, by name. A step's allowlist is the ONLY way a tool reaches a
  * model: sttp-ai answers "Tool not found" for a call to anything not offered.
  */
final class Toolbox(contributed: List[ToolContribution]):
  private val byName = contributed.map(c => c.name -> c).toMap

  /** Every tool's name, in the order of registration. */
  def names: List[ToolName] = contributed.map(_.name)

  /** The tools for an allowlist, in its order. */
  def tools(allow: List[ToolName]): List[AgentTool[Identity, ?]] = allow.distinct.flatMap(tool)

  def tool(t: ToolName): Option[AgentTool[Identity, ?]] = byName.get(t).map(_.agentTool)

object Toolbox:
  /** A tool's input schema as `/mcp/agent/<agent>` lists it, and so as every turn of a step re-sends it. */
  def inputSchema(t: AgentTool[Identity, ?]): io.circe.Json =
    import sttp.apispec.circe.*
    import io.circe.syntax.*
    def untitled(j: io.circe.Json) = j.mapObject(_.remove("title"))
    untitled(t.jsonSchema.asJson.deepDropNullValues).mapObject { o =>
      val top = o.remove("$schema")
      top("$defs")
        .flatMap(_.asObject)
        .fold(top)(ds => top.add("$defs", io.circe.Json.fromJsonObject(ds.mapValues(untitled))))
    }
