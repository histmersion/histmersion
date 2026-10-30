package com.histmersion.config.edit

class SettingKeySuite extends munit.FunSuite:

  test("a key with or without histmersion. is the same key"):
    assertEquals(SettingKey.parse("backups.keep").map(k => k.value: String), Right("histmersion.backups.keep"))
    assertEquals(
      SettingKey.parse(" histmersion.backups.keep ").map(k => k.value: String),
      Right("histmersion.backups.keep")
    )
    assert(SettingKey.parse("").isLeft)
    assert(SettingKey.parse("histmersion").isLeft)
    assert(SettingKey.parse("backups..keep").isLeft)
    assert(SettingKey.parse("backups.keep = 3").isLeft)

  test("its parts"):
    val k = SettingKey.parse("plugins.1password.enabled").toOption.get
    assertEquals(k.relative, "plugins.1password.enabled")
    assertEquals(k.leaf, "enabled")
    assertEquals(k.parent.map(p => p.value: String), Some("histmersion.plugins.1password"))
    assert(k.within(SettingKey.parse("plugins").toOption.get))
    assert(!k.within(SettingKey.parse("plugin").toOption.get))
    assertEquals(SettingKey.fromHocon(k.hoconPath).map(p => p.value: String), Some(k.value: String))
