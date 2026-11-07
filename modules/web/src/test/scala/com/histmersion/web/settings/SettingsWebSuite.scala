package com.histmersion.web.settings

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.api.settings.ConfigEdit
import com.histmersion.api.instance.MapStartConfig
import com.histmersion.api.plugins.PluginStatus
import com.histmersion.web.map.GeoBounds

class SettingsWebSuite extends munit.FunSuite:

  test("a value as HOCON writes it, as a person types it"):
    assertEquals(Draft.plain(Some(com.histmersion.web.settings.Draft.Text("\"a \\\"b\\\" c\""))), "a \"b\" c")
    assertEquals(Draft.plain(Some(com.histmersion.web.settings.Draft.Text("5"))), "5")
    assertEquals(Draft.plain(Some(com.histmersion.web.settings.Draft.Text("[a,b]"))), "[a,b]")
    assertEquals(Draft.plain(None), "")
    assert(Draft.isOn(com.histmersion.web.settings.Draft.Text("true")) && Draft.isOn(com.histmersion.web.settings.Draft.Text(" Yes ")) && !Draft.isOn(com.histmersion.web.settings.Draft.Text("false")) && !Draft.isOn(com.histmersion.web.settings.Draft.Text("")))

  test("a setting's name is its last word, as words"):
    assertEquals(SettingControl.nameOf(com.histmersion.api.settings.ConfigEdit.Field.Key("histmersion.acquire.contact-email")), "Contact email")
    assertEquals(SettingControl.nameOf(com.histmersion.api.settings.ConfigEdit.Field.Key("backups.keep")), "Keep")
    assertEquals(SettingControl.defaultText(com.histmersion.api.settings.ConfigEdit.Field.Kind("flag"), com.histmersion.web.settings.Draft.Text("true")), "On by default.")
    assertEquals(SettingControl.defaultText(com.histmersion.api.settings.ConfigEdit.Field.Kind("flag"), com.histmersion.web.settings.Draft.Text("false")), "Off by default.")
    assertEquals(SettingControl.defaultText(com.histmersion.api.settings.ConfigEdit.Field.Kind("number"), com.histmersion.web.settings.Draft.Text("5")), "Default: 5.")

  test("the disk's share: what Histmersion takes, against the free space left"):
    assertEquals(DataSection.share(com.histmersion.api.ByteCount(0), com.histmersion.api.ByteCount(100)), 0)
    assertEquals(DataSection.share(com.histmersion.api.ByteCount(25), com.histmersion.api.ByteCount(100)), 25)
    assertEquals(DataSection.share(com.histmersion.api.ByteCount(10), com.histmersion.api.ByteCount(0)), 100)
    assertEquals(DataSection.share(com.histmersion.api.ByteCount(500), com.histmersion.api.ByteCount(100)), 100)

  test("a credential's name for a person"):
    assertEquals(KeysSection.name(com.histmersion.api.settings.ConfigEdit.Field.LocalKey("plugins.oa-core.api-key")), "Open access: CORE: API key")
    assertEquals(KeysSection.name(com.histmersion.api.settings.ConfigEdit.Field.LocalKey("auth.api-key")), "This server's own key for changes")

  test("a key's reference, and how a credential is held — never the value"):
    import KeysSection.Form
    assertEquals(KeysSection.reference(Form.Env, com.histmersion.web.settings.KeysSection.ReferenceEntry(" HISTMERSION_CORE_KEY ")), Right("env:HISTMERSION_CORE_KEY"))
    assert(KeysSection.reference(Form.Env, com.histmersion.web.settings.KeysSection.ReferenceEntry("not a name")).isLeft)
    assertEquals(
      KeysSection.reference(Form.Keepassxc, com.histmersion.web.settings.KeysSection.ReferenceEntry("Histmersion/CORE#password")),
      Right("keepassxc:Histmersion/CORE#password")
    )
    assert(KeysSection.reference(Form.Bitwarden, com.histmersion.web.settings.KeysSection.ReferenceEntry("  ")).isLeft)
    assertEquals(KeysSection.state(ConfigEdit.Credential(com.histmersion.api.settings.ConfigEdit.Credential.Rule("reference"), com.histmersion.api.settings.ConfigEdit.Credential.Form("unset"))), "Not set")
    assertEquals(
      KeysSection.state(ConfigEdit.Credential(com.histmersion.api.settings.ConfigEdit.Credential.Rule("reference"), com.histmersion.api.settings.ConfigEdit.Credential.Form("vault"), Some(com.histmersion.api.settings.ConfigEdit.Credential.Shown("keepassxc:X")))),
      "Set · from KeePassXC"
    )
    assert(
      KeysSection
        .state(ConfigEdit.Credential(com.histmersion.api.settings.ConfigEdit.Credential.Rule("reference"), com.histmersion.api.settings.ConfigEdit.Credential.Form("value"), Some(com.histmersion.api.settings.ConfigEdit.Credential.Shown("value:***"))))
        .value
        .contains("written in the settings file")
    )

  test("the advanced filter finds a setting by its key or its words"):
    val f = ConfigEdit.Field(com.histmersion.api.settings.ConfigEdit.Field.Key("histmersion.backups.keep"), com.histmersion.api.settings.ConfigEdit.Field.Section("backups"), com.histmersion.api.settings.ConfigEdit.Field.Kind("number"), doc = com.histmersion.api.settings.ConfigEdit.Field.Doc("archives left after a dump"))
    assert(AdvancedSection.matches(f, com.histmersion.web.settings.AdvancedSection.Query("")))
    assert(AdvancedSection.matches(f, com.histmersion.web.settings.AdvancedSection.Query("KEEP")))
    assert(AdvancedSection.matches(f, com.histmersion.web.settings.AdvancedSection.Query("dump")))
    assert(!AdvancedSection.matches(f, com.histmersion.web.settings.AdvancedSection.Query("timeout")))
    assertEquals(AdvancedSection.sectionName(com.histmersion.api.settings.ConfigEdit.Section.Id("db")), "Database")
    assertEquals(AdvancedSection.sectionName(com.histmersion.api.settings.ConfigEdit.Section.Id("backups")), "Backups")

  test("where the map opens: the zoom that shows a place whole, and what is written"):
    assertEquals(StartPlace.zoomFor(GeoBounds(com.histmersion.web.map.GeoBounds.South(36), com.histmersion.web.map.GeoBounds.West(26), com.histmersion.web.map.GeoBounds.North(42), com.histmersion.web.map.GeoBounds.East(45))), 5.0)
    assertEquals(StartPlace.zoomFor(GeoBounds(GeoBounds.South(37.6), GeoBounds.West(22.7), GeoBounds.North(37.6), GeoBounds.East(22.7))), 9.0)
    assert(StartPlace.zoomFor(GeoBounds(com.histmersion.web.map.GeoBounds.South(-60), com.histmersion.web.map.GeoBounds.West(-180), com.histmersion.web.map.GeoBounds.North(80), com.histmersion.web.map.GeoBounds.East(180))).value >= 2.0)
    val c = MapStartConfig(com.histmersion.api.instance.MapStartMode.Wire("place"), Latitude(39.123456), Longitude(35.5), com.histmersion.api.instance.MapStartConfig.Zoom(5), com.histmersion.api.instance.MapStartConfig.Label("Anatolia"))
    assertEquals(
      StartPlace.changes(c).toMap,
      Map(
        "ui.map-start.mode" -> "place",
        "ui.map-start.lat" -> "39.1235",
        "ui.map-start.lon" -> "35.5",
        "ui.map-start.zoom" -> "5",
        "ui.map-start.label" -> "Anatolia"
      )
    )
    assert(StartPlace.describe(c).value.contains("Anatolia"))
    assert(StartPlace.describe(c.modify(_.mode).setTo(com.histmersion.api.instance.MapStartMode.Wire("last"))).value.startsWith("The first time"))

  test("a plugin's state in words"):
    assertEquals(PluginsSection.state(PluginStatus(com.histmersion.api.plugins.PluginStatus.Id("x"), com.histmersion.api.plugins.PluginStatus.Version("1"), com.histmersion.api.plugins.PluginStatus.SpiVersion(2), enabled = com.histmersion.api.plugins.PluginStatus.Enabled(true), state = com.histmersion.api.plugins.PluginStatus.State("registered"))), "Working")
    assertEquals(PluginsSection.state(PluginStatus(com.histmersion.api.plugins.PluginStatus.Id("x"), com.histmersion.api.plugins.PluginStatus.Version("1"), com.histmersion.api.plugins.PluginStatus.SpiVersion(2), enabled = com.histmersion.api.plugins.PluginStatus.Enabled(false), state = com.histmersion.api.plugins.PluginStatus.State("disabled"))), "Turned off")
    assert(
      PluginsSection
        .state(PluginStatus(com.histmersion.api.plugins.PluginStatus.Id("x"), com.histmersion.api.plugins.PluginStatus.Version("1"), com.histmersion.api.plugins.PluginStatus.SpiVersion(2), enabled = com.histmersion.api.plugins.PluginStatus.Enabled(true), state = com.histmersion.api.plugins.PluginStatus.State("failed"), error = Some(com.histmersion.api.plugins.PluginDescription.Error("no key"))))
        .value
        .contains("no key")
    )

  test("what a plugin adds, in words"):
    assertEquals(
      PluginsSection.provides(
        PluginStatus(
          com.histmersion.api.plugins.PluginStatus.Id("oa-core"),
          com.histmersion.api.plugins.PluginStatus.Version("1"),
          com.histmersion.api.plugins.PluginStatus.SpiVersion(2),
          enabled = com.histmersion.api.plugins.PluginStatus.Enabled(true),
          state = com.histmersion.api.plugins.PluginStatus.State("registered"),
          provides = List(com.histmersion.api.plugins.PluginDescription.Provides("source-fetcher:oa-core"), com.histmersion.api.plugins.PluginDescription.Provides("source-fetcher:core-index"))
        )
      ),
      List("Finds papers")
    )

  test("each section's lead"):
    assert(SettingsPages.lead(com.histmersion.web.SettingsSection.Keys).exists(_.value.contains("Never the key itself")))
