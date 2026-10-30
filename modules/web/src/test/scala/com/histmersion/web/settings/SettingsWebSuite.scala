package com.histmersion.web.settings

import com.histmersion.api.{ConfigEdit, MapStartConfig, PluginStatus}
import com.histmersion.web.map.GeoBounds

class SettingsWebSuite extends munit.FunSuite:

  test("a value as HOCON writes it, as a person types it"):
    assertEquals(Draft.plain(Some("\"a \\\"b\\\" c\"")), "a \"b\" c")
    assertEquals(Draft.plain(Some("5")), "5")
    assertEquals(Draft.plain(Some("[a,b]")), "[a,b]")
    assertEquals(Draft.plain(None), "")
    assert(Draft.isOn("true") && Draft.isOn(" Yes ") && !Draft.isOn("false") && !Draft.isOn(""))

  test("a setting's name is its last word, as words"):
    assertEquals(SettingControl.nameOf("histmersion.acquire.contact-email"), "Contact email")
    assertEquals(SettingControl.nameOf("backups.keep"), "Keep")

  test("the disk's share: what Histmersion takes, against the free space left"):
    assertEquals(DataSection.share(0, 100), 0)
    assertEquals(DataSection.share(25, 100), 25)
    assertEquals(DataSection.share(10, 0), 100)
    assertEquals(DataSection.share(500, 100), 100)

  test("a credential's name for a person"):
    assertEquals(KeysSection.name("plugins.oa-core.api-key"), "Open access: CORE: API key")
    assertEquals(KeysSection.name("auth.api-key"), "This server's own key for changes")

  test("a key's reference, and how a credential is held — never the value"):
    import KeysSection.Form
    assertEquals(KeysSection.reference(Form.Env, " HISTMERSION_CORE_KEY "), Right("env:HISTMERSION_CORE_KEY"))
    assert(KeysSection.reference(Form.Env, "not a name").isLeft)
    assertEquals(KeysSection.reference(Form.Keepassxc, "Histmersion/CORE#password"), Right("keepassxc:Histmersion/CORE#password"))
    assert(KeysSection.reference(Form.Bitwarden, "  ").isLeft)
    assertEquals(KeysSection.state(ConfigEdit.Credential("reference", "unset")), "Not set")
    assertEquals(KeysSection.state(ConfigEdit.Credential("reference", "vault", Some("keepassxc:X"))), "Set · from KeePassXC")
    assert(KeysSection.state(ConfigEdit.Credential("reference", "value", Some("value:***"))).contains("written in the settings file"))

  test("the advanced filter finds a setting by its key or its words"):
    val f = ConfigEdit.Field("histmersion.backups.keep", "backups", "number", doc = "archives left after a dump")
    assert(AdvancedSection.matches(f, ""))
    assert(AdvancedSection.matches(f, "KEEP"))
    assert(AdvancedSection.matches(f, "dump"))
    assert(!AdvancedSection.matches(f, "timeout"))

  test("where the map opens: the zoom that shows a place whole, and what is written"):
    assertEquals(StartPlace.zoomFor(GeoBounds(36, 26, 42, 45)), 5.0)
    assertEquals(StartPlace.zoomFor(GeoBounds(37.6, 22.7, 37.6, 22.7)), 9.0)
    assert(StartPlace.zoomFor(GeoBounds(-60, -180, 80, 180)) >= 2.0)
    val c = MapStartConfig("place", 39.123456, 35.5, 5, "Anatolia")
    assertEquals(StartPlace.changes(c).toMap, Map("ui.map-start.mode" -> "place", "ui.map-start.lat" -> "39.1235",
      "ui.map-start.lon" -> "35.5", "ui.map-start.zoom" -> "5", "ui.map-start.label" -> "Anatolia"))
    assert(StartPlace.describe(c).contains("Anatolia"))
    assert(StartPlace.describe(c.copy(mode = "last")).startsWith("The first time"))

  test("a plugin's state in words"):
    assertEquals(PluginsSection.state(PluginStatus("x", "1", 2, enabled = true, state = "registered")), "Working")
    assertEquals(PluginsSection.state(PluginStatus("x", "1", 2, enabled = false, state = "disabled")), "Turned off")
    assert(PluginsSection.state(PluginStatus("x", "1", 2, enabled = true, state = "failed", error = Some("no key"))).contains("no key"))

  test("each section's lead"):
    assert(SettingsPages.lead(com.histmersion.web.SettingsSection.Keys).exists(_.contains("Never the key itself")))
