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
  object Surface extends Newtype[String, Pure]
  type Runner = Runner.T
  object Runner extends Newtype[String, Pure]
  type Job = Job.T
  object Job extends Newtype[String, Pure]
  type Actor = Actor.T
  object Actor extends Newtype[String, Pure]
  type Via = Via.T
  object Via extends Newtype[String, Pure]
  type Plugin = Plugin.T
  object Plugin extends Newtype[String, Pure]:
    def of[T](name: T)(using RefinedType.Mirror[T]): Plugin = Plugin(s"$name")
  type Workflow = Workflow.T
  object Workflow extends Newtype[String, Pure]:
    def of[T](name: T)(using RefinedType.Mirror[T]): Workflow = Workflow(s"$name")
  type Definition = Definition.T
  object Definition extends Newtype[String, Pure]:
    def of[T](name: T)(using RefinedType.Mirror[T]): Definition = Definition(s"$name")
  type Provider = Provider.T
  object Provider extends Newtype[String, Pure]:
    def of[T](name: T)(using RefinedType.Mirror[T]): Provider = Provider(s"$name")
  type Model = Model.T
  object Model extends Newtype[String, Pure]:
    def of[T](name: T)(using RefinedType.Mirror[T]): Model = Model(s"$name")

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
