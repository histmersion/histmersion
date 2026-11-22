package com.histmersion
package cli

import com.histmersion.api.automation.Queue.{CreatedBy, Creation}
import com.histmersion.config.CliConfig

final case class AgentIdentity(
    plugin: Option[Creation.Plugin] = None,
    workflow: Option[Creation.Workflow] = None,
    definition: Option[Creation.Definition] = None,
    provider: Option[Creation.Provider] = None,
    model: Option[Creation.Model] = None
) derives CanEqual:
  def orElse(fallback: AgentIdentity): AgentIdentity =
    AgentIdentity(
      plugin.orElse(fallback.plugin),
      workflow.orElse(fallback.workflow),
      definition.orElse(fallback.definition),
      provider.orElse(fallback.provider),
      model.orElse(fallback.model)
    )

  def createdBy: Option[CreatedBy] =
    Option.when(List(plugin, workflow, definition, provider, model).exists(_.isDefined))(
      CreatedBy(plugin, workflow, definition, provider, model)
    )

object AgentIdentity:
  val Nobody: AgentIdentity = AgentIdentity()

  def configured(agent: CliConfig.Agent): AgentIdentity =
    def named(name: CliConfig.Agent.Name): Option[CliConfig.Agent.Name] = Option.when(s"$name".trim.nonEmpty)(name)
    AgentIdentity(
      named(agent.plugin).map(Creation.Plugin.of(_)),
      named(agent.workflow).map(Creation.Workflow.of(_)),
      named(agent.definition).map(Creation.Definition.of(_)),
      named(agent.provider).map(Creation.Provider.of(_)),
      named(agent.model).map(Creation.Model.of(_))
    )
