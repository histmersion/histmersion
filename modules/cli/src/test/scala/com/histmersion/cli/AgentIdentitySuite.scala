package com.histmersion
package cli

import com.histmersion.api.automation.Queue.{CreatedBy, Creation}
import com.histmersion.config.AppConfig
import org.ekrich.config.ConfigFactory

import scala.jdk.CollectionConverters.*

class AgentIdentitySuite extends munit.FunSuite:

  private def configured(env: Map[String, String]): AgentIdentity =
    AgentIdentity.configured(AppConfig.load(ConfigFactory.parseMap(env.asJava)).cli.agent)

  private val everyVariable = Map(
    "HISTMERSION_AGENT_PLUGIN" -> "research-workspace",
    "HISTMERSION_AGENT_WORKFLOW" -> "trailhead",
    "HISTMERSION_AGENT_DEFINITION" -> "article-skeptic",
    "HISTMERSION_AGENT_PROVIDER" -> "anthropic",
    "HISTMERSION_AGENT_MODEL" -> "claude-opus-5-5"
  )

  test("no variable set: nobody, and the add carries no identity (the person at the command line)"):
    assertEquals(configured(Map.empty), AgentIdentity.Nobody)
    assertEquals(configured(Map.empty).createdBy, None)
    assertEquals(configured(Map("HISTMERSION_AGENT_PLUGIN" -> "  ")).createdBy, None)

  test("each environment variable fills its part"):
    assertEquals(
      configured(everyVariable).createdBy,
      Some(
        CreatedBy(
          Some(Creation.Plugin("research-workspace")),
          Some(Creation.Workflow("trailhead")),
          Some(Creation.Definition("article-skeptic")),
          Some(Creation.Provider("anthropic")),
          Some(Creation.Model("claude-opus-5-5"))
        )
      )
    )

  test("a flag wins over its variable; a part with no flag falls back to its variable"):
    val flags = AgentIdentity(workflow = Some(Creation.Workflow("bookkeeping")), model = Some(Creation.Model("m2")))
    val merged = flags.orElse(configured(everyVariable))
    assertEquals(merged.workflow, Some(Creation.Workflow("bookkeeping")))
    assertEquals(merged.model, Some(Creation.Model("m2")))
    assertEquals(merged.plugin, Some(Creation.Plugin("research-workspace")))
    assertEquals(merged.provider, Some(Creation.Provider("anthropic")))
