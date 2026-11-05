package com.histmersion.domain.automation.agents

import scala.annotation.tailrec
import com.histmersion.api.automation.Agents.Role
import com.histmersion.domain.automation.workflows.ToolName

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

  /** The definition as the fingerprint of generated trees reads it: its fields as a case class prints them, each tool
    * by the name the toolbox's enumeration gave it (`GetSource` for `get_source`), its web access as its case was
    * called (`Search`) — the fingerprint installed trees and their manifests were generated under, so a tree generated
    * from the same definitions is not rewritten.
    */
  def fingerprint: String =
    productIterator.zipWithIndex
      .map((v, i) =>
        productElementName(i) match
          case "tools" => tools.map(AgentDef.enumerated).toString
          case "web"   => AgentDef.enumerated(web)
          case _       => String.valueOf(v)
      )
      .mkString(productPrefix + "(", ",", ")")

object AgentDef:
  /** `get_source` as `GetSource`. */
  def enumerated(t: ToolName): String = t.value.split('_').map(_.capitalize).mkString

  /** A web access as its enumeration printed it when the fingerprint was first taken (`search` as `Search`). */
  def enumerated(w: WebAccess): String = w match
    case WebAccess.WebSearch => "Search"
    case other               => other.toString
