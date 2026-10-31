package com.histmersion.domain.automation.workflows

import com.histmersion.values.AgentRole

/** The engine's registry of contributed workflows and tools, in the order they were registered. Built once by
  * [[Workflows.of]], which refuses two workflows with one identifier, two tools with one name, and a workflow whose
  * roles may use a tool nobody contributes.
  */
final class Workflows private (val definitions: List[WorkflowDefinition], val tools: List[ToolContribution]):
  private val byId: Map[String, WorkflowDefinition] = definitions.map(d => (d.id.value: String) -> d).toMap

  /** The workflow a run names (trimmed, any case), if registered. */
  def parse(raw: String): Option[WorkflowDefinition] = byId.get(raw.trim.toLowerCase)

  def definition(id: WorkflowId): Option[WorkflowDefinition] = byId.get(id.value: String)

  /** Every contributed tool's name, in the order of registration. */
  def toolNames: List[ToolName] = tools.map(_.name)

  /** This registry with `definition` in place of the one registered under its identifier (or added). */
  def replaced(definition: WorkflowDefinition): Workflows =
    val rest = definitions.filterNot(_.id == definition.id)
    new Workflows(
      if rest.size == definitions.size then definitions :+ definition
      else definitions.map(d => if d.id == definition.id then definition else d),
      tools
    )

object Workflows:
  def of(contributions: Contributions): Either[String, Workflows] =
    val Contributions(definitions, tools) = contributions
    val twice = definitions.groupBy(_.id.value).collect { case (id, ds) if ds.size > 1 => id }.toList.sorted
    val twiceTools = tools.groupBy(_.name.value).collect { case (n, ts) if ts.size > 1 => n }.toList.sorted
    val known = tools.map(_.name).toSet
    val unknown = definitions.flatMap { d =>
      val missing = AgentRole.values.toList.flatMap(r => d.allowedTools(r).filterNot(known)).map(_.value).distinct
      Option.when(missing.nonEmpty)(s"${d.id.value} names tools nobody contributes: ${missing.sorted.mkString(", ")}")
    }
    if twice.nonEmpty then Left(s"workflows registered twice: ${twice.mkString(", ")}")
    else if twiceTools.nonEmpty then Left(s"agent tools registered twice: ${twiceTools.mkString(", ")}")
    else if unknown.nonEmpty then Left(unknown.mkString("; "))
    else Right(new Workflows(definitions, tools))
