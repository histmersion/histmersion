package com.histmersion.server.plugins

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.server.ServerPlugins
import com.histmersion.util.plugins.HostProvided

import com.histmersion.pluginhost.{FirstPartySettings, PluginHost, ReservedNames}
import com.histmersion.domain.catalogue.places.BuiltinGazetteers
import com.histmersion.server.agents.BuiltinAi
import com.histmersion.domain.catalogue.events.BuiltinCatalogues
import com.histmersion.domain.media.imports.BuiltinMedia

/** The plugin host refuses a plugin's contribution under a name of one of the server's own providers; it keeps those
  * names itself (the plugin testkit runs it without the server), so they must be the names the server's built-in
  * registries answer to.
  */
class ReservedNamesSuite extends munit.FunSuite:
  test("the plugin host reserves exactly the names of the server's built-in providers"):
    assertEquals(ReservedNames.mediaCollections, BuiltinMedia.reserved)
    assertEquals(ReservedNames.eventCatalogues, BuiltinCatalogues.reserved)
    assertEquals(ReservedNames.aiProviders, BuiltinAi.reserved)
    assertEquals(ReservedNames.gazetteerSources, BuiltinGazetteers.reserved)

  test("the first-party plugins are the ones every image holds: found on its class path or supplied by the host"):
    val ids = PluginHost.discover(getClass.getClassLoader)._1.map(_.info.id) ++ ServerPlugins.providers.map(_.info.id)
    assertEquals(ids.toSet, ReservedNames.firstPartyPlugins)
    assertEquals(ids.size, ids.distinct.size)

  test("the relink tool keeps the ids of the plugins the host supplies itself without loading them"):
    assertEquals(HostProvided.Ids, ServerPlugins.providers.map(_.info.id))

  test("the settings table names every first-party plugin but the vaults, which are rebuilt as any plugin is"):
    assertEquals(
      FirstPartySettings.all.map(_.plugin).toSet,
      ReservedNames.firstPartyPlugins -- Set(com.histmersion.pluginhost.ReservedNames.Name("keepassxc"), com.histmersion.pluginhost.ReservedNames.Name("1password"), com.histmersion.pluginhost.ReservedNames.Name("bitwarden"))
    )
