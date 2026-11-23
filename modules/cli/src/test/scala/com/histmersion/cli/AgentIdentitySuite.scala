package com.histmersion
package cli

import com.histmersion.config.AppConfig
import org.ekrich.config.ConfigFactory

import scala.jdk.CollectionConverters.*
import com.histmersion.api.Provenance

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
        Provenance.By(
          Some(Provenance.Plugin("research-workspace")),
          Some(Provenance.Workflow("trailhead")),
          Some(Provenance.Definition("article-skeptic")),
          Some(Provenance.Provider("anthropic")),
          Some(Provenance.Model("claude-opus-5-5"))
        )
      )
    )

  test("a flag wins over its variable; a part with no flag falls back to its variable"):
    val flags = AgentIdentity(workflow = Some(Provenance.Workflow("bookkeeping")), model = Some(Provenance.Model("m2")))
    val merged = flags.orElse(configured(everyVariable))
    assertEquals(merged.workflow, Some(Provenance.Workflow("bookkeeping")))
    assertEquals(merged.model, Some(Provenance.Model("m2")))
    assertEquals(merged.plugin, Some(Provenance.Plugin("research-workspace")))
    assertEquals(merged.provider, Some(Provenance.Provider("anthropic")))
