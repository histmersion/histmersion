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
    name: AgentDef.Name,
    description: AgentDef.Description,
    role: Role,
    skills: List[AgentDef.Skills],
    include: List[AgentDef.Include],
    tools: List[ToolName],
    web: WebAccess,
    writes: Writes,
    maxTurns: AgentDef.MaxTurns,
    answer: Option[AgentDef.Answer],
    runtimes: Set[AgentRuntime],
    interactive: Option[InteractiveSpec],
    body: AgentDef.Body
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
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Skills = Skills.T
  object Skills extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Include = Include.T
  object Include extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MaxTurns = MaxTurns.T
  object MaxTurns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Answer = Answer.T
  object Answer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def enumerated(t: ToolName): String = t.value.split('_').map(_.capitalize).mkString

  /** A web access as its enumeration printed it when the fingerprint was first taken (`search` as `Search`). */
  def enumerated(w: WebAccess): String = w match
    case WebAccess.WebSearch => "Search"
    case other               => other.toString
