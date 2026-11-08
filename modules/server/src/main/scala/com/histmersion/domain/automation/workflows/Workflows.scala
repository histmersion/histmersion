package com.histmersion
package domain.automation.workflows

import com.histmersion.domain.automation.goals.ObjectiveProgress
import com.histmersion.domain.automation.managers.ManagerKindDefinition
import com.histmersion.domain.automation.AgentRole
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.research.Slug

/** The engine's registry of contributed workflows, tools and manager kinds, in the order they were registered. Built
  * once by [[Workflows.of]], which refuses two workflows with one identifier, two tools with one name, a workflow whose
  * roles may use a tool nobody contributes, two manager kinds of one kind and an item kind two of them drive.
  */
final class Workflows private (
    val definitions: List[WorkflowDefinition],
    val tools: List[ToolContribution],
    val managerKinds: List[ManagerKindDefinition],
    val objectives: List[ObjectiveProgress]
):
  private val byId: Map[WorkflowId, WorkflowDefinition] = definitions.map(d => d.id -> d).toMap

  /** The workflow a run names (trimmed, any case), if registered. */
  def parse(raw: String): Option[WorkflowDefinition] = WorkflowId.option(raw.trim.toLowerCase).flatMap(byId.get)

  def definition(id: WorkflowId): Option[WorkflowDefinition] = byId.get(id)

  /** Every goal's linked items that exist, by goal, as the domains count them. */
  def linked(): Map[Goal.Slug, List[Slug]] =
    objectives.flatMap(_.linked().toList).groupMap(_._1)(_._2).view.mapValues(_.flatten).toMap

  /** The linked items that count as finished. */
  def finished(): Set[Slug] = objectives.flatMap(_.finished()).toSet

  /** Every contributed tool's name, in the order of registration. */
  def toolNames: List[ToolName] = tools.map(_.name)

  /** This registry with `definition` in place of the one registered under its identifier (or added). */
  def replaced(definition: WorkflowDefinition): Workflows =
    val rest = definitions.filterNot(_.id == definition.id)
    new Workflows(
      if rest.size == definitions.size then definitions :+ definition
      else definitions.map(d => if d.id == definition.id then definition else d),
      tools,
      managerKinds,
      objectives
    )

object Workflows:
  def of(contributions: Contributions): Either[String, Workflows] =
    val Contributions(definitions, tools, kinds, objectives) = contributions
    val twice = definitions.groupBy(_.id.value).collect { case (id, ds) if ds.size > 1 => id }.toList.sorted
    val twiceTools = tools.groupBy(_.name.value).collect { case (n, ts) if ts.size > 1 => n }.toList.sorted
    val known = tools.map(_.name).toSet
    val unknown = definitions.flatMap { d =>
      val missing = AgentRole.values.toList.flatMap(r => d.allowedTools(r).filterNot(known)).map(_.value).distinct
      Option.when(missing.nonEmpty)(s"${d.id.value} names tools nobody contributes: ${missing.sorted.mkString(", ")}")
    }
    val twiceKinds = kinds.groupBy(_.kind.wire.value).collect { case (k, ks) if ks.size > 1 => k }.toList.sorted
    val twiceItems =
      kinds.flatMap(_.items).groupBy(_.value).collect { case (i, is) if is.size > 1 => i }.toList.sorted
    if twice.nonEmpty then Left(s"workflows registered twice: ${twice.mkString(", ")}")
    else if twiceTools.nonEmpty then Left(s"agent tools registered twice: ${twiceTools.mkString(", ")}")
    else if unknown.nonEmpty then Left(unknown.mkString("; "))
    else if twiceKinds.nonEmpty then Left(s"manager kinds registered twice: ${twiceKinds.mkString(", ")}")
    else if twiceItems.nonEmpty then Left(s"item kinds two manager kinds drive: ${twiceItems.mkString(", ")}")
    else Right(new Workflows(definitions, tools, kinds, objectives))
