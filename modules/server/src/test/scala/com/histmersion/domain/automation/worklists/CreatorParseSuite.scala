package com.histmersion
package domain.automation.worklists

import com.histmersion.domain.Creator
import com.histmersion.util.problem.Problem
import com.histmersion.api.Provenance

class CreatorParseSuite extends munit.FunSuite:

  private val surfaces = Creator.Surface.values.toList

  private val plugin = Provenance.Plugin("research-workspace")
  private val workflow = Provenance.Workflow("review-article")
  private val definition = Provenance.Definition("article-skeptic")
  private val provider = Provenance.Provider("anthropic")
  private val model = Provenance.Model("claude-opus-5-5")

  private def subsets[A](as: List[A]): List[List[A]] =
    as.foldRight(List(List.empty[A]))((a, rest) => rest ++ rest.map(a :: _))

  private enum Part derives CanEqual:
    case Plugin, Workflow, Definition, Provider, Model

  private def record(present: Set[Part]): Provenance.By =
    Provenance.By(
      Option.when(present(Part.Plugin))(plugin),
      Option.when(present(Part.Workflow))(workflow),
      Option.when(present(Part.Definition))(definition),
      Option.when(present(Part.Provider))(provider),
      Option.when(present(Part.Model))(model)
    )

  private val skeptic = Creator.Agent.Model(
    Creator.Definition.Name("article-skeptic"),
    Creator.Provider.Name("anthropic"),
    Creator.LanguageModel.Name("claude-opus-5-5")
  )

  test(
    "every record of present and absent names: accepted exactly when plugin and workflow are both there and the agent's three names are all there or none"
  ):
    val agentParts = Set(Part.Definition, Part.Provider, Part.Model)
    for
      present <- subsets(Part.values.toList).map(_.toSet)
      surface <- surfaces
    do
      val agentNames = present.intersect(agentParts)
      val valid = present(Part.Plugin) && present(Part.Workflow) && (agentNames.isEmpty || agentNames == agentParts)
      val parsed = WorkListControllers.creatorOf(Some(record(present)), surface)
      assertEquals(parsed.isRight, valid, (present, surface, parsed).toString)
      parsed.foreach {
        case Creator.New.Automated(p, w, agent) =>
          assertEquals((s"$p", s"$w"), (s"$plugin", s"$workflow"))
          assertEquals(agent, if agentNames.isEmpty then Creator.Agent.Code else skeptic)
        case other => fail("a record parses as Automated, not " + other.toString)
      }

  test("a partial agent is refused as names that go together; a missing plugin or workflow as missing under createdBy"):
    val partial =
      WorkListControllers.creatorOf(Some(record(Set(Part.Plugin, Part.Workflow, Part.Model))), Creator.Surface.Cli)
    assert(
      partial.left.exists {
        case Problem.Invalid.Inconsistent(fields, Problem.Invalid.Exclusion.Together, at) =>
          fields.toVector.map(f => s"$f") == Vector("agentDefinition", "provider", "model") &&
          at.map(f => s"$f") == Vector("createdBy")
        case _ => false
      },
      partial
    )
    val noWorkflow = WorkListControllers.creatorOf(Some(record(Set(Part.Plugin))), Creator.Surface.Rest)
    assert(
      noWorkflow.left.exists {
        case Problem.Invalid.Missing(field, at) => s"$field" == "workflow" && at.map(f => s"$f") == Vector("createdBy")
        case _                                  => false
      },
      noWorkflow
    )

  test("a name its rule refuses is a malformed value, whichever part it is"):
    val bad = List(
      Provenance.By(Some(Provenance.Plugin("Not A Name")), Some(workflow)),
      Provenance.By(Some(plugin), Some(Provenance.Workflow(""))),
      Provenance.By(Some(plugin), Some(workflow), Some(Provenance.Definition("UPPER")), Some(provider), Some(model)),
      Provenance
        .By(Some(plugin), Some(workflow), Some(definition), Some(Provenance.Provider("has space")), Some(model)),
      Provenance.By(
        Some(plugin),
        Some(workflow),
        Some(definition),
        Some(provider),
        Some(Provenance.Model(" leading-space"))
      )
    )
    bad.foreach { b =>
      val parsed = WorkListControllers.creatorOf(Some(b), Creator.Surface.Rest)
      assert(parsed.left.exists(_.isInstanceOf[Problem.Invalid.Malformed]), s"$b: $parsed")
    }

  test("no record: the person through the surface; over MCP it is refused"):
    surfaces.filterNot(_ == Creator.Surface.Mcp).foreach { s =>
      assertEquals(WorkListControllers.creatorOf(None, s), Right(Creator.New.Manual(s)))
    }
    assert(
      WorkListControllers.creatorOf(None, Creator.Surface.Mcp).left.exists {
        case Problem.Invalid.Missing(field, _) => s"$field" == "createdBy"
        case _                                 => false
      }
    )

  test("the surface a REST client names: cli, web or rest; none is rest; anything else is refused"):
    assertEquals(WorkListControllers.surfaceOf(None), Right(Creator.Surface.Rest))
    assertEquals(WorkListControllers.surfaceOf(Some(Provenance.Cli)), Right(Creator.Surface.Cli))
    assertEquals(WorkListControllers.surfaceOf(Some(Provenance.Web)), Right(Creator.Surface.Web))
    assertEquals(WorkListControllers.surfaceOf(Some(Provenance.Rest)), Right(Creator.Surface.Rest))
    assert(WorkListControllers.surfaceOf(Some(Provenance.Via("mcp"))).isLeft)

  test("an actor is person or agents; anything else is refused"):
    assertEquals(WorkListControllers.actorOf(Some(Provenance.Agents)), Right(Some(Provenance.Agents)))
    assertEquals(WorkListControllers.actorOf(None), Right(None))
    assert(WorkListControllers.actorOf(Some(Provenance.Actor("robot"))).isLeft)

  test("a new creator's actor: the person for Manual, the agents for Automated"):
    assertEquals(Creator.New.Manual(Creator.Surface.Web).actor, domain.Actor.Person)
    assertEquals(Creator.code(Creator.Plugin.Name("library"), Creator.Workflow.Name("seed")).actor, domain.Actor.Agents)
