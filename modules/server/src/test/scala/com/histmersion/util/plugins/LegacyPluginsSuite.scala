package com.histmersion
package util.plugins

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.util.settings.SettingsService
import org.ekrich.config.ConfigFactory

import scala.jdk.CollectionConverters.*
import com.histmersion.lint.{LintRule, tolerated}

/** [[LegacyPlugins.migrate]] alone: pure config in, config and warnings out (docs/research-plugins.md §20.2). What the
  * mapped config does to the plugins is in BuiltinFetchersSuite.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class LegacyPluginsSuite extends munit.FunSuite:

  private val known: Plugin.Id => Set[SettingsService.ConfigPath] =
    Map(
      Plugin.Id("oa-unpaywall") -> Set(SettingsService.ConfigPath("contact-email")),
      Plugin.Id("oa-core") -> Set(SettingsService.ConfigPath("api-key"))
    ).withDefaultValue(Set.empty)
  private def migrate(user: String, env: Map[String, String] = Map.empty) =
    LegacyPlugins.migrate(ConfigFactory.parseString(user), ConfigFactory.parseMap(env.asJava), known)

  test("nothing of oa-index: the config and the environment come back as they were"):
    val user = ConfigFactory.parseString("histmersion.plugins.arxiv.enabled = false")
    val env = ConfigFactory.parseMap(Map("PATH" -> "/bin").asJava)
    val m = LegacyPlugins.migrate(user, env, known)
    assert(m.user.eq(user))
    assert(m.env.eq(env))
    assertEquals(m.warnings, Nil)

  test("the block is removed and enabled copied to each successor that does not set its own"):
    val m = migrate("histmersion.plugins { oa-index.enabled = false, oa-core.enabled = true }")
    assert(!m.user.hasPath("histmersion.plugins.oa-index"))
    assertEquals(
      LegacyPlugins.OaIndex.into.map(id => id -> m.user.getBoolean(s"histmersion.plugins.$id.enabled")),
      List("oa-unpaywall" -> false, "oa-core" -> true, "oa-openalex" -> false, "oa-landing" -> false)
    )
    assertEquals(m.warnings.size, 1)

  test("a key goes only where the successor's defaults have it, and never over the successor's own"):
    val m = migrate(
      """histmersion.plugins { oa-index { api-key = "env:A", contact-email = "a@b.c" }, oa-unpaywall.contact-email = "own@b.c" }"""
    )
    assertEquals(m.user.getString("histmersion.plugins.oa-core.api-key"), "env:A")
    assertEquals(m.user.getString("histmersion.plugins.oa-unpaywall.contact-email"), "own@b.c")
    assert(!m.user.hasPath("histmersion.plugins.oa-openalex.api-key"))

  test("oa-index that is no block: removed, said"):
    val m = migrate("histmersion.plugins.oa-index = false")
    assert(!m.user.hasPath("histmersion.plugins.oa-index"))
    assert(m.warnings.exists(_.value.contains("must be a block")), m.warnings)

  test("the environment: the former variable fills only the successors' variables that are not set"):
    val m = migrate(
      "",
      Map("HISTMERSION_PLUGIN_OA_INDEX_ENABLED" -> "false", "HISTMERSION_PLUGIN_OA_LANDING_ENABLED" -> "true")
    )
    assertEquals(m.env.getString("\"HISTMERSION_PLUGIN_OA_CORE_ENABLED\""), "false")
    assertEquals(m.env.getString("\"HISTMERSION_PLUGIN_OA_LANDING_ENABLED\""), "true")
    assertEquals(m.user, ConfigFactory.parseString(""))

  test("order: oa-index replaced in place; a successor already listed is not repeated"):
    val m = migrate("histmersion.plugins.order = [oa-core, oa-index, pmc]")
    assertEquals(
      m.user.getStringList("histmersion.plugins.order").asScala.toList,
      List("oa-core", "oa-unpaywall", "oa-openalex", "oa-landing", "pmc")
    )

  test("expand and the environment name"):
    assertEquals(LegacyPlugins.expand(Plugin.Id("oa-index")), LegacyPlugins.OaIndex.into)
    assertEquals(LegacyPlugins.expand(Plugin.Id("pmc")), List("pmc"))
    assertEquals(LegacyPlugins.enabledEnv(Plugin.Id("oa-unpaywall")), "HISTMERSION_PLUGIN_OA_UNPAYWALL_ENABLED")
    assertEquals(LegacyPlugins.ids, Set("oa-index"))
