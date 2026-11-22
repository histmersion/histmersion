package com.histmersion
package domain.automation.worklists

import com.histmersion.domain.{Actor, Creator}
import com.histmersion.util.database.Jsonb
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
        Agent("model", Some(s"$definition"), Some(s"$provider"), Some(s"$model"))

  def of(creator: Creator): StoredCreator = creator match
    case Creator.Manual(surface)                    => StoredCreator("manual", Some(surface.toString.toLowerCase))
    case Creator.Automated(plugin, workflow, agent) =>
      StoredCreator(
        "automated",
        None,
        known(plugin).map(p => s"$p"),
        known(workflow).map(w => s"$w"),
        known(agent).map(Agent.of)
      )
    case Creator.Unattributed(surface) => StoredCreator("unattributed", Some(surface.toString.toLowerCase))
    case Creator.Unknown(Creator.Facts.Unrecorded(job)) => StoredCreator("unknown", job = job.map(j => s"$j"))
    case Creator.Unknown(Creator.Facts.Unreadable(_))   => StoredCreator("unknown")

  private def known[A](part: Creator.Part[A]): Option[A] = part match
    case Creator.Part.Known(name) => Some(name)
    case Creator.Part.Unknown     => None

  def write(creator: Creator): Jsonb.JsonText = Jsonb.write(of(creator), Jsonb.DropNulls(true))

  def actor(creator: Creator.New): String = creator.actor match
    case Actor.Person => "person"
    case Actor.Agents => "agents"
