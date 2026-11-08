package com.histmersion
package web.settings

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.catalogue.{Latitude, Longitude}
import com.histmersion.api.settings.ConfigEdit
import com.histmersion.api.instance.MapStartConfig
import com.histmersion.api.plugins.PluginStatus
import com.histmersion.web.map.GeoBounds
import com.histmersion.api.ByteCount
import com.histmersion.api.instance.MapStartMode
import com.histmersion.api.plugins.PluginDescription
import com.histmersion.web.SettingsSection

class SettingsWebSuite extends munit.FunSuite:

  test("a value as HOCON writes it, as a person types it"):
    assertEquals(Draft.plain(Some(Draft.Text("\"a \\\"b\\\" c\""))), "a \"b\" c")
    assertEquals(Draft.plain(Some(Draft.Text("5"))), "5")
    assertEquals(Draft.plain(Some(Draft.Text("[a,b]"))), "[a,b]")
    assertEquals(Draft.plain(None), "")
    assert(
      Draft.isOn(Draft.Text("true")) && Draft.isOn(Draft.Text(" Yes ")) && !Draft.isOn(Draft.Text("false")) && !Draft
        .isOn(Draft.Text(""))
    )

  test("a setting's name is its last word, as words"):
    assertEquals(SettingControl.nameOf(ConfigEdit.Field.Key("histmersion.acquire.contact-email")), "Contact email")
    assertEquals(SettingControl.nameOf(ConfigEdit.Field.Key("backups.keep")), "Keep")
    assertEquals(SettingControl.defaultText(ConfigEdit.Field.Kind("flag"), Draft.Text("true")), "On by default.")
    assertEquals(SettingControl.defaultText(ConfigEdit.Field.Kind("flag"), Draft.Text("false")), "Off by default.")
    assertEquals(SettingControl.defaultText(ConfigEdit.Field.Kind("number"), Draft.Text("5")), "Default: 5.")

  test("the disk's share: what Histmersion takes, against the free space left"):
    assertEquals(DataSection.share(ByteCount(0), ByteCount(100)), 0)
    assertEquals(DataSection.share(ByteCount(25), ByteCount(100)), 25)
    assertEquals(DataSection.share(ByteCount(10), ByteCount(0)), 100)
    assertEquals(DataSection.share(ByteCount(500), ByteCount(100)), 100)

  test("a credential's name for a person"):
    assertEquals(KeysSection.name(ConfigEdit.Field.LocalKey("plugins.oa-core.api-key")), "Open access: CORE: API key")
    assertEquals(KeysSection.name(ConfigEdit.Field.LocalKey("auth.api-key")), "This server's own key for changes")

  test("a key's reference, and how a credential is held — never the value"):
    import KeysSection.Form
    assertEquals(
      KeysSection.reference(Form.Env, KeysSection.ReferenceEntry(" HISTMERSION_CORE_KEY ")),
      Right("env:HISTMERSION_CORE_KEY")
    )
    assert(KeysSection.reference(Form.Env, KeysSection.ReferenceEntry("not a name")).isLeft)
    assertEquals(
      KeysSection.reference(Form.Keepassxc, KeysSection.ReferenceEntry("Histmersion/CORE#password")),
      Right("keepassxc:Histmersion/CORE#password")
    )
    assert(KeysSection.reference(Form.Bitwarden, KeysSection.ReferenceEntry("  ")).isLeft)
    assertEquals(
      KeysSection.state(
        ConfigEdit.Credential(ConfigEdit.Credential.Rule("reference"), ConfigEdit.Credential.Form("unset"))
      ),
      "Not set"
    )
    assertEquals(
      KeysSection.state(
        ConfigEdit.Credential(
          ConfigEdit.Credential.Rule("reference"),
          ConfigEdit.Credential.Form("vault"),
          Some(ConfigEdit.Credential.Shown("keepassxc:X"))
        )
      ),
      "Set · from KeePassXC"
    )
    assert(
      KeysSection
        .state(
          ConfigEdit.Credential(
            ConfigEdit.Credential.Rule("reference"),
            ConfigEdit.Credential.Form("value"),
            Some(ConfigEdit.Credential.Shown("value:***"))
          )
        )
        .value
        .contains("written in the settings file")
    )

  test("the advanced filter finds a setting by its key or its words"):
    val f = ConfigEdit.Field(
      ConfigEdit.Field.Key("histmersion.backups.keep"),
      ConfigEdit.Field.Section("backups"),
      ConfigEdit.Field.Kind("number"),
      doc = ConfigEdit.Field.Doc("archives left after a dump")
    )
    assert(AdvancedSection.matches(f, AdvancedSection.Query("")))
    assert(AdvancedSection.matches(f, AdvancedSection.Query("KEEP")))
    assert(AdvancedSection.matches(f, AdvancedSection.Query("dump")))
    assert(!AdvancedSection.matches(f, AdvancedSection.Query("timeout")))
    assertEquals(AdvancedSection.sectionName(ConfigEdit.Section.Id("db")), "Database")
    assertEquals(AdvancedSection.sectionName(ConfigEdit.Section.Id("backups")), "Backups")

  test("where the map opens: the zoom that shows a place whole, and what is written"):
    assertEquals(
      StartPlace.zoomFor(GeoBounds(GeoBounds.South(36), GeoBounds.West(26), GeoBounds.North(42), GeoBounds.East(45))),
      5.0
    )
    assertEquals(
      StartPlace.zoomFor(
        GeoBounds(GeoBounds.South(37.6), GeoBounds.West(22.7), GeoBounds.North(37.6), GeoBounds.East(22.7))
      ),
      9.0
    )
    assert(
      StartPlace
        .zoomFor(GeoBounds(GeoBounds.South(-60), GeoBounds.West(-180), GeoBounds.North(80), GeoBounds.East(180)))
        .value >= 2.0
    )
    val c = MapStartConfig(
      MapStartMode.Wire("place"),
      Latitude(39.123456),
      Longitude(35.5),
      MapStartConfig.Zoom(5),
      MapStartConfig.Label("Anatolia")
    )
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
    assert(StartPlace.describe(c.modify(_.mode).setTo(MapStartMode.Wire("last"))).value.startsWith("The first time"))

  test("a plugin's state in words"):
    assertEquals(
      PluginsSection.state(
        PluginStatus(
          PluginStatus.Id("x"),
          PluginStatus.Version("1"),
          PluginStatus.SpiVersion(2),
          enabled = PluginStatus.Enabled(true),
          state = PluginStatus.State("registered")
        )
      ),
      "Working"
    )
    assertEquals(
      PluginsSection.state(
        PluginStatus(
          PluginStatus.Id("x"),
          PluginStatus.Version("1"),
          PluginStatus.SpiVersion(2),
          enabled = PluginStatus.Enabled(false),
          state = PluginStatus.State("disabled")
        )
      ),
      "Turned off"
    )
    assert(
      PluginsSection
        .state(
          PluginStatus(
            PluginStatus.Id("x"),
            PluginStatus.Version("1"),
            PluginStatus.SpiVersion(2),
            enabled = PluginStatus.Enabled(true),
            state = PluginStatus.State("failed"),
            error = Some(PluginDescription.Error("no key"))
          )
        )
        .value
        .contains("no key")
    )

  test("what a plugin adds, in words"):
    assertEquals(
      PluginsSection.provides(
        PluginStatus(
          PluginStatus.Id("oa-core"),
          PluginStatus.Version("1"),
          PluginStatus.SpiVersion(2),
          enabled = PluginStatus.Enabled(true),
          state = PluginStatus.State("registered"),
          provides = List(
            PluginDescription.Provides("source-fetcher:oa-core"),
            PluginDescription.Provides("source-fetcher:core-index")
          )
        )
      ),
      List("Finds papers")
    )

  test("each section's lead"):
    assert(SettingsPages.lead(SettingsSection.Keys).exists(_.value.contains("Never the key itself")))
