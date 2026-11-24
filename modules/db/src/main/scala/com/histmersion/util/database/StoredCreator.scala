package com.histmersion
package util.database

import com.histmersion.domain.{Actor, Creator}
import com.histmersion.util.json.JsonCodec
import com.histmersion.util.json.JsonCodec.given
import StoredCreator.Agent

final private[histmersion] case class StoredCreator(
    kind: String,
    surface: Option[String] = None,
    plugin: Option[String] = None,
    workflow: Option[String] = None,
    agent: Option[Agent] = None,
    job: Option[String] = None
) derives JsonCodec

object StoredCreator:

  final case class Agent(
      kind: String,
      definition: Option[String] = None,
      provider: Option[String] = None,
      model: Option[String] = None
  ) derives JsonCodec

  object Agent:
    def of(agent: Creator.Agent): Agent = agent match
      case Creator.Agent.Code                               => Agent("code")
      case Creator.Agent.Model(definition, provider, model) =>
        modelOf(Some(definition), Some(s"$provider"), Some(s"$model"))
      case Creator.Agent.RunModels(definition) => modelOf(definition, None, None)

    private def modelOf(
        definition: Option[Creator.Definition.Name],
        provider: Option[String],
        model: Option[String]
    ): Agent = Agent("model", definition.map(d => s"$d"), provider, model)

  private def surfaceText(surface: Creator.Surface): String = surface.toString.toLowerCase

  def of(creator: Creator): StoredCreator = creator match
    case Creator.Manual(surface)                             => StoredCreator("manual", known(surface).map(surfaceText))
    case Creator.Automated(plugin, workflow, agent, surface) =>
      StoredCreator(
        "automated",
        known(surface).map(surfaceText),
        known(plugin).map(p => s"$p"),
        known(workflow).map(w => s"$w"),
        known(agent).map(Agent.of)
      )
    case Creator.Unattributed(surface)                  => StoredCreator("unattributed", Some(surfaceText(surface)))
    case Creator.Unknown(Creator.Facts.Unrecorded(job)) => StoredCreator("unknown", job = job.map(j => s"$j"))
    case Creator.Unknown(Creator.Facts.Unreadable(_))   => StoredCreator("unknown")

  private def known[A](part: Creator.Part[A]): Option[A] = part match
    case Creator.Part.Known(name) => Some(name)
    case Creator.Part.Unknown     => None

  def write(creator: Creator): Jsonb.JsonText = Jsonb.write(of(creator), Jsonb.DropNulls(true))

  def column(creator: Creator.New): Jsonb.JsonText = write(creator.stored)

  def actor(creator: Creator.New): String = creator.actor match
    case Actor.Person => "person"
    case Actor.Agents => "agents"
