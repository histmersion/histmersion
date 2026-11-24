package com.histmersion
package api.automation

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.RefinedType

final case class Creation(
    kind: Creation.Kind,
    surface: Option[Creation.Surface] = None,
    plugin: Option[Creation.Plugin] = None,
    workflow: Option[Creation.Workflow] = None,
    agent: Option[Creation.Agent] = None,
    job: Option[Creation.Job] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object Creation:

  trait Named:
    def createdBy: Option[By]

  trait Requested extends Named:
    def via: Option[Via]

  final case class By(
      plugin: Option[Plugin] = None,
      workflow: Option[Workflow] = None,
      agentDefinition: Option[Definition] = None,
      provider: Option[Provider] = None,
      model: Option[Model] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  final case class Agent(
      kind: Runner,
      definition: Option[Definition] = None,
      provider: Option[Provider] = None,
      model: Option[Model] = None
  ) derives JsonCodec,
        KindlingsSchema,
        api.json.Schema

  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Surface = Surface.T
  object Surface extends Newtype[String, Pure]:
    extension (s: Surface)
      def shown: String = s.value match
        case "web"      => "the web page"
        case "cli"      => "the command line"
        case "rest"     => "the REST API"
        case "mcp"      => "the MCP"
        case "internal" => "the server itself"
        case other      => other
  type Runner = Runner.T
  object Runner extends Newtype[String, Pure]
  type Job = Job.T
  object Job extends Newtype[String, Pure]
  type Actor = Actor.T
  object Actor extends Newtype[String, Pure]
  type Via = Via.T
  object Via extends Newtype[String, Pure]
  type At = At.T
  object At extends Newtype[String, Pure]:
    extension (at: At) def shown: String = at.value
  type Plugin = Plugin.T
  object Plugin extends Newtype[String, Pure]:
    def of[T](name: T)(using RefinedType.Mirror[T]): Plugin = Plugin(s"$name")
    extension (p: Plugin) def shown: String = p.value
  type Workflow = Workflow.T
  object Workflow extends Newtype[String, Pure]:
    def of[T](name: T)(using RefinedType.Mirror[T]): Workflow = Workflow(s"$name")
    extension (w: Workflow) def shown: String = w.value
  type Definition = Definition.T
  object Definition extends Newtype[String, Pure]:
    def of[T](name: T)(using RefinedType.Mirror[T]): Definition = Definition(s"$name")
    extension (d: Definition) def shown: String = d.value
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]:
    def of[T](name: T)(using RefinedType.Mirror[T]): Provider = Provider(s"$name")
    extension (p: Provider) def shown: String = p.value
  type Model = Model.T
  object Model extends Newtype[String, Pure]:
    def of[T](name: T)(using RefinedType.Mirror[T]): Model = Model(s"$name")
    extension (m: Model) def shown: String = m.value

  extension (agent: Agent)
    def shown: String = (agent.definition, agent.provider, agent.model) match
      case _ if agent.kind == Code     => "code"
      case (Some(d), Some(p), Some(m)) => s"the agent ${d.shown} (${p.shown} ${m.shown})"
      case (Some(d), _, _)             => s"a model of the agent ${d.shown}"
      case _                           => "a model"

  extension (creation: Creation)
    def who: String = creation.kind match
      case k if k == Manual       => "a person"
      case k if k == Unattributed => "an unnamed caller"
      case k if k == Automated    =>
        val agent = creation.agent.fold("an automated caller")(_.shown)
        val workflow = creation.workflow.fold("")(w => s" in workflow ${w.shown}")
        val plugin = creation.plugin.fold("")(p => s" of ${p.shown}")
        agent + workflow + plugin
      case _ => "someone not recorded"

  def line(creator: Option[Creation], at: Option[At]): Option[String] =
    Option.when(creator.isDefined || at.isDefined)(
      (List("Created") ++ creator.map(c => s"by ${c.who}") ++ creator.flatMap(_.surface).map(s => s"via ${s.shown}") ++
        at.map(a => s"at ${a.shown}")).mkString(" ")
    )

  val Manual: Kind = Kind("manual")
  val Automated: Kind = Kind("automated")
  val Unattributed: Kind = Kind("unattributed")
  val Unknown: Kind = Kind("unknown")
  val Unreadable: Kind = Kind("unreadable")
  val Code: Runner = Runner("code")
  val ByModel: Runner = Runner("model")
  val Person: Actor = Actor("person")
  val Agents: Actor = Actor("agents")
  val Actors: List[Actor] = List(Person, Agents)
  val Cli: Via = Via("cli")
  val Web: Via = Via("web")
  val Rest: Via = Via("rest")
  val Vias: List[Via] = List(Rest, Cli, Web)
