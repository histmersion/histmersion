package com.histmersion
package domain.automation.agents
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.domain.automation.workflows.{ToolContribution, ToolName}
import com.histmersion.domain.Creator
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
  def tools(allow: List[ToolName])(using Creator.New): List[AgentTool[Identity, ?]] = allow.distinct.flatMap(tool)

  def tool(t: ToolName)(using Creator.New): Option[AgentTool[Identity, ?]] = byName.get(t).map(_.agentTool)

object Toolbox:
  /** A tool's input schema as `/mcp/agent/<agent>` lists it, and so as every turn of a step re-sends it. */
  def inputSchema(t: AgentTool[Identity, ?]): Json =
    def untitled(j: Json) = j.asObject.fold(j)(o => Json.fromJsonObject(o.remove("title")))
    untitled(SttpAiJson.inputSchema(t)).asObject.fold(Json.Null) { o =>
      val top = o.remove("$schema")
      Json.fromJsonObject(
        top("$defs")
          .flatMap(_.asObject)
          .fold(top)(ds => top.updated("$defs", Json.fromJsonObject(ds.mapValues(untitled))))
      )
    }
