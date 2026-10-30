package com.histmersion.server.plugins

import com.histmersion.pluginhost.ReservedNames
import com.histmersion.server.agents.BuiltinAi
import com.histmersion.server.events.BuiltinCatalogues
import com.histmersion.server.media.BuiltinMedia

/** The plugin host refuses a plugin's contribution under a name of one of the server's own providers; it keeps those
  * names itself (the plugin testkit runs it without the server), so they must be the names the server's built-in
  * registries answer to.
  */
class ReservedNamesSuite extends munit.FunSuite:
  test("the plugin host reserves exactly the names of the server's built-in providers"):
    assertEquals(ReservedNames.mediaCollections, BuiltinMedia.reserved)
    assertEquals(ReservedNames.eventCatalogues, BuiltinCatalogues.reserved)
    assertEquals(ReservedNames.aiProviders, BuiltinAi.reserved)
