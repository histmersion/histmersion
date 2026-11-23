package com.histmersion
package cli

import com.histmersion.config.CliConfig
import com.histmersion.api.Provenance

final case class AgentIdentity(
    plugin: Option[Provenance.Plugin] = None,
    workflow: Option[Provenance.Workflow] = None,
    definition: Option[Provenance.Definition] = None,
    provider: Option[Provenance.Provider] = None,
    model: Option[Provenance.Model] = None
) derives CanEqual:
  def orElse(fallback: AgentIdentity): AgentIdentity =
    AgentIdentity(
      plugin.orElse(fallback.plugin),
      workflow.orElse(fallback.workflow),
      definition.orElse(fallback.definition),
      provider.orElse(fallback.provider),
      model.orElse(fallback.model)
    )

  def createdBy: Option[Provenance.By] =
    Option.when(List(plugin, workflow, definition, provider, model).exists(_.isDefined))(
      Provenance.By(plugin, workflow, definition, provider, model)
    )

object AgentIdentity:
  val Nobody: AgentIdentity = AgentIdentity()

  def configured(agent: CliConfig.Agent): AgentIdentity =
    def named(name: CliConfig.Agent.Name): Option[CliConfig.Agent.Name] = Option.when(s"$name".trim.nonEmpty)(name)
    AgentIdentity(
      named(agent.plugin).map(Provenance.Plugin.of(_)),
      named(agent.workflow).map(Provenance.Workflow.of(_)),
      named(agent.definition).map(Provenance.Definition.of(_)),
      named(agent.provider).map(Provenance.Provider.of(_)),
      named(agent.model).map(Provenance.Model.of(_))
    )
