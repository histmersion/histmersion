package com.histmersion
package domain

import chimp.server.{NoStructuredOutput, ToolResult}
import com.histmersion.api.Api
import com.histmersion.api.automation.Creation
import com.histmersion.util.mcp.McpOut
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.util.problem.Problem

final case class CreatorControllers(createdBy: Option[Creation.By], via: Option[Creation.Via]):

  def overRest: Either[Problem, Creator.New] =
    CreatorControllers
      .surfaceOf(via)
      .flatMap(surface =>
        CreatorControllers.creatorOf(
          createdBy,
          surface,
          if via.isEmpty then Creator.New.Unattributed(Creator.Surface.Rest) else Creator.New.Manual(surface)
        )
      )

  def overMcp: Either[Problem, Creator.New] =
    CreatorControllers.creatorOf(createdBy, Creator.Surface.Mcp, Creator.New.Unattributed(Creator.Surface.Mcp))

object CreatorControllers:

  def rest[A](in: Creation.Requested)(body: Creator.New ?=> Either[Api.Error, A]): Either[Api.Error, A] =
    CreatorControllers(in.createdBy, in.via).overRest.left.map(Api.Error(_)).flatMap(creator => body(using creator))

  def mcp(in: Creation.Named)(
      body: Creator.New ?=> ToolResult[NoStructuredOutput]
  ): ToolResult[NoStructuredOutput] =
    CreatorControllers(in.createdBy, None).overMcp match
      case Right(creator) => body(using creator)
      case Left(problem)  => McpOut.error(Api.Error(problem))

  def creatorOf(by: Option[Creation.By], surface: Creator.Surface, unnamed: Creator.New): Either[Problem, Creator.New] =
    by match
      case None    => Right(unnamed)
      case Some(b) =>
        for
          plugin <- named(b.plugin, Problem.Field("plugin"))(Creator.Plugin.Name.from(_))
          workflow <- named(b.workflow, Problem.Field("workflow"))(Creator.Workflow.Name.from(_))
          agent <- agentOf(b)
        yield Creator.New.Automated(plugin, workflow, agent, surface)

  private val surfaces: List[(Creation.Via, Creator.Surface)] =
    List(
      Creation.Rest -> Creator.Surface.Rest,
      Creation.Cli -> Creator.Surface.Cli,
      Creation.Web -> Creator.Surface.Web
    )

  def surfaceOf(via: Option[Creation.Via]): Either[Problem, Creator.Surface] =
    via.fold(Right(Creator.Surface.Rest)) { v =>
      surfaces
        .collectFirst { case (w, s) if w == v => s }
        .toRight(
          Problem.Invalid.Unknown(
            Problem.TypeName.of(Creation.Via),
            Problem.Raw.of(v),
            Creation.Vias.map(Problem.Raw.of(_)).toVector,
            Vector(Problem.Field("via"))
          )
        )
    }

  private val CreatedByField = Problem.Field("createdBy")

  private def named[R, A](raw: Option[R], field: Problem.Field)(
      check: R => Either[Newtype.Refusal, A]
  ): Either[Problem, A] =
    raw
      .toRight(Problem.Invalid.Missing(field, Vector(CreatedByField)))
      .flatMap(check(_).left.map(Problem.Invalid.malformed))

  private def agentOf(b: Creation.By): Either[Problem, Creator.Agent] =
    (b.agentDefinition, b.provider, b.model) match
      case (None, None, None)          => Right(Creator.Agent.Code)
      case (Some(d), Some(p), Some(m)) =>
        for
          definition <- Creator.Definition.Name.from(d).left.map(Problem.Invalid.malformed)
          provider <- Creator.Provider.Name.from(p).left.map(Problem.Invalid.malformed)
          model <- Creator.LanguageModel.Name.from(m).left.map(Problem.Invalid.malformed)
        yield Creator.Agent.Model(definition, provider, model)
      case _ =>
        Left(
          Problem.Invalid.Inconsistent(
            NonEmptyVector.of(Problem.Field("agentDefinition"), Problem.Field("provider"), Problem.Field("model")),
            Problem.Invalid.Exclusion.Together,
            Vector(CreatedByField)
          )
        )
