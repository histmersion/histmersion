package com.histmersion
package config.edit

import com.histmersion.config.testsupport.ComparedByValue.given

class ConfigSchemaSuite extends munit.FunSuite:
  private val schema = ConfigSchema.reference
  private def key(s: String) = SettingKey.parse(SettingKey.ParseRaw(s)).toOption.get
  private def field(s: String) = schema.field(key(s)).getOrElse(fail(s"no field $s"))

  test("every leaf of reference.conf is a field, with its kind and default"):
    assert(schema.fields.size > 600, s"${schema.fields.size} fields")
    assertEquals(field("backups.keep").kind, SettingKind.Number)
    assertEquals(field("backups.keep").default, Some(SettingValue.Number(BigDecimal(5))))
    assertEquals(field("config.watch").kind, SettingKind.Flag)
    assertEquals(field("acquire.candidates.indexes").kind, SettingKind.Items)
    assertEquals(field("ui.base-map-source").default, Some(SettingValue.Text(SettingValue.Text.Value("auto"))))
    assertEquals(field("acquire.contact-email").default, Some(SettingValue.Text(SettingValue.Text.Value(""))))

  test("the documentation: the comment at the line's end, its environment line's, and the lines that continue them"):
    val keep = field("backups.keep").doc
    assert(keep.value.startsWith("archives left after a dump"), keep)
    assert(keep.value.contains("0 keeps every one"), keep)
    val narrow = field("ui.narrow-max-width-px").doc
    assertEquals(narrow, "at or below this viewport width the web UI switches to its phone layout (map first, drawers)")
    assertEquals(
      field("ui.marker-radius").doc,
      "drawn radius of a finding dot, px; clustering joins dots whose centres are closer than 2 x this (they overlap)"
    )
    assertEquals(field("ui.viewport-debounce-ms").doc, "\"only papers in view\": delay after moveend/zoomend")
    assert(
      field("ui.base-map-source").doc.value.startsWith("which base maps the page offers"),
      field("ui.base-map-source").doc
    )
    assertEquals(field("ui.debounce.reload-ms").doc, "State change → /api/findings + /api/sources")

  test("each environment variable is the one reference.conf names under the key"):
    assertEquals(field("backups.keep").envVar, Some("HISTMERSION_BACKUPS_KEEP"))
    assertEquals(field("acquire.contact-email").envVar, Some("HISTMERSION_ACQUIRE_CONTACT_EMAIL"))
    assertEquals(field("ui.narrow-max-width-px").envVar, Some("HISTMERSION_UI_NARROW_MAX_WIDTH_PX"))
    assertEquals(field("ui.marker-radius").envVar, None)

  test("restart-only settings, credentials and open blocks"):
    assert(field("server.port").restart.isDefined)
    assert(field("runtime.pg-port").restart.isDefined, "runtime.* is restart-only as a block")
    assertEquals(field("backups.keep").restart, None)
    assertEquals(field("auth.api-key").secret, SecretRule.FromEnvironment)
    val plugin = field("plugins.oa-core.api-key")
    assertEquals(plugin.kind, SettingKind.Any)
    assertEquals(plugin.secret, SecretRule.Reference)
    assertEquals(
      schema.field(key("plugins.whatever.enabled")).map(_.kind),
      Some(SettingKind.Flag),
      "a plugin's block is open; enabled is a flag"
    )
    assert(schema.field(key("backups.kep")).isEmpty)
    assert(schema.isBlock(key("backups")))
    assertEquals(schema.closest(key("backups.kep")).map(_.relative).headOption, Some("backups.keep"))

  test("the sections are reference.conf's top-level blocks, in its order"):
    val names = schema.sections.map(_._1.value)
    assertEquals(names.take(3), List("server", "db", "converter"))
    assert(names.contains("setup") && names.contains("basemap"))
    assert(schema.blockDoc(key("config")).value.startsWith("reloading this config in place"), schema.blockDoc(key("config")))
