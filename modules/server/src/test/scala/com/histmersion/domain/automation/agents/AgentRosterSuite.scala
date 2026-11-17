package com.histmersion
package domain.automation.agents

import com.histmersion.util.problem.Problem
import hearth.kindlings.optics.syntax.*

import com.histmersion.server.agents.EngineContributions
import com.histmersion.domain.automation.AgentRole
import com.histmersion.util.storage.Stores
import com.histmersion.util.storage.Resources
import com.histmersion.util.system.JvmHome

/** The definitions take only the roles the contributed workflows run, as they list only the contributed tools. */
class AgentRosterSuite extends munit.FunSuite:

  private given Stores = Stores.disk
  private given Resources = Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given JvmHome = JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  test("every bundled definition takes a contributed role"):
    val defs = AgentDefinitions(new Prompts(Prompts.OverrideDir("")), EngineContributions.roster)
    assert(defs.agents.isRight, defs.agents.left.toOption.getOrElse(""))

  test("the roster holds every role a registered pipeline runs, and the probe"):
    assertEquals(EngineContributions.roster.roles.toSet, AgentRole.values.toSet)

  test("a definition whose role no workflow contributes is refused, naming the roles there are"):
    val roster = EngineContributions.roster
    val without = roster.modify(_.roles).setTo(roster.roles.filterNot(_ == AgentRole.Skeptic))
    val skeptic = AgentDefinitions(new Prompts(Prompts.OverrideDir("")), without).agentNames
      .map(AgentDefinitions(new Prompts(Prompts.OverrideDir("")), without).agent)
    val refused = skeptic.collect { case Left(Problem.Config.Definition(file, flaw: Problem.Config.Flaw.RoleUnused)) =>
      (file, flaw)
    }
    assertEquals(
      refused,
      List(
        (
          Problem.Raw("agents/article-skeptic.md"),
          Problem.Config.Flaw
            .RoleUnused(Problem.Raw("skeptic"), without.roles.toVector.map(r => Problem.Raw.of(r.wire)))
        )
      )
    )
    assert(!AgentDefinitions(new Prompts(Prompts.OverrideDir("")), without).agents.isRight)
