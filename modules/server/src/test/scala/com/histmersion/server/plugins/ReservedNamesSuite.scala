package com.histmersion
package server.plugins

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.server.ServerPlugins
import com.histmersion.util.plugins.HostProvided

import com.histmersion.pluginhost.{FirstPartySettings, PluginHost, ReservedNames}
import com.histmersion.domain.catalogue.places.BuiltinGazetteers
import com.histmersion.server.agents.BuiltinAi
import com.histmersion.domain.catalogue.events.BuiltinCatalogues
import com.histmersion.domain.media.imports.BuiltinMedia
import com.histmersion.lint.{LintRule, tolerated}

/** The plugin host refuses a plugin's contribution under a name of one of the server's own providers; it keeps those
  * names itself (the plugin testkit runs it without the server), so they must be the names the server's built-in
  * registries answer to.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ReservedNamesSuite extends munit.FunSuite:
  test("the plugin host reserves exactly the names of the server's built-in providers"):
    assertEquals(ReservedNames.mediaCollections.map(_.value: String), BuiltinMedia.reserved.map(_.value: String))
    assertEquals(ReservedNames.eventCatalogues.map(_.value: String), BuiltinCatalogues.reserved.map(_.value: String))
    assertEquals(ReservedNames.aiProviders.map(_.value: String), BuiltinAi.reserved.map(_.value: String))
    assertEquals(ReservedNames.gazetteerSources.map(_.value: String), BuiltinGazetteers.reserved.map(_.value: String))

  test("the first-party plugins are the ones every image holds: found on its class path or supplied by the host"):
    val ids = PluginHost.discover(getClass.getClassLoader)._1.map(_.info.id) ++ ServerPlugins.providers.map(_.info.id)
    assertEquals(ids.toSet, ReservedNames.firstPartyPlugins)
    assertEquals(ids.size, ids.distinct.size)

  test("the relink tool keeps the ids of the plugins the host supplies itself without loading them"):
    assertEquals(HostProvided.Ids.map(_.value: String), ServerPlugins.providers.map(_.info.id.value: String))

  test("the settings table names every first-party plugin but the vaults, which are rebuilt as any plugin is"):
    assertEquals(
      FirstPartySettings.all.map(_.plugin).toSet,
      ReservedNames.firstPartyPlugins -- Set(
        ReservedNames.Name("keepassxc"),
        ReservedNames.Name("1password"),
        ReservedNames.Name("bitwarden")
      )
    )
