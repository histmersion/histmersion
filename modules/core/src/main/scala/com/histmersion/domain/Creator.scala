package com.histmersion
package domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match

import Creator.{Agent, Definition, Facts, LanguageModel, Part, Plugin, Provider, Surface, Workflow}
import Creator.Facts.{Job, Stored}

enum Creator derives CanEqual:
  case Manual(surface: Part[Surface])
  case Automated(plugin: Part[Plugin.Name], workflow: Part[Workflow.Name], agent: Part[Agent], surface: Part[Surface])
  case Unattributed(surface: Surface)
  case Unknown(facts: Facts)

object Creator:

  enum Surface derives CanEqual:
    case Rest, Mcp, Cli, Web, Internal

  enum Part[+A] derives CanEqual:
    case Known(name: A)
    case Unknown

  enum Agent derives CanEqual:
    case Model(definition: Definition.Name, provider: Provider.Name, model: LanguageModel.Name)
    case Code

  enum Facts derives CanEqual:
    case Unrecorded(job: Option[Job])
    case Unreadable(stored: Stored)

  object Facts:
    type Job = Job.T
    object Job extends Newtype[String, Pure]
    type Stored = Stored.T
    object Stored extends Newtype[String, Pure]

  type NameRule = Match["[a-z][a-z0-9_-]*"]
  type ModelRule = Match["[A-Za-z0-9][A-Za-z0-9._:/@+-]*"]

  object Plugin:
    type Name = Name.T
    object Name extends Newtype[String, NameRule]:
      def from[R](raw: R)(using RefinedType.Mirror[R]): Either[Newtype.Refusal, Name] = checked(s"$raw")

  object Workflow:
    type Name = Name.T
    object Name extends Newtype[String, NameRule]:
      def from[R](raw: R)(using RefinedType.Mirror[R]): Either[Newtype.Refusal, Name] = checked(s"$raw")

  object Definition:
    type Name = Name.T
    object Name extends Newtype[String, NameRule]:
      def from[R](raw: R)(using RefinedType.Mirror[R]): Either[Newtype.Refusal, Name] = checked(s"$raw")

  object Provider:
    type Name = Name.T
    object Name extends Newtype[String, NameRule]:
      def from[R](raw: R)(using RefinedType.Mirror[R]): Either[Newtype.Refusal, Name] = checked(s"$raw")

  object LanguageModel:
    type Name = Name.T
    object Name extends Newtype[String, ModelRule]:
      def from[R](raw: R)(using RefinedType.Mirror[R]): Either[Newtype.Refusal, Name] = checked(s"$raw")

  enum New derives CanEqual:
    case Manual(surface: Surface)
    case Automated(plugin: Plugin.Name, workflow: Workflow.Name, agent: Agent, surface: Surface)
    case Unattributed(surface: Surface)

  extension (creator: New)
    def stored: Creator = creator match
      case New.Manual(surface)                             => Manual(Part.Known(surface))
      case New.Automated(plugin, workflow, agent, surface) =>
        Automated(Part.Known(plugin), Part.Known(workflow), Part.Known(agent), Part.Known(surface))
      case New.Unattributed(surface) => Unattributed(surface)

    def actor: Actor = creator match
      case New.Manual(_) | New.Unattributed(_) => Actor.Person
      case New.Automated(_, _, _, _)           => Actor.Agents

  def code(plugin: Plugin.Name, workflow: Workflow.Name): New =
    New.Automated(plugin, workflow, Agent.Code, Surface.Internal)

  val unrecorded: Creator = Unknown(Facts.Unrecorded(None))
