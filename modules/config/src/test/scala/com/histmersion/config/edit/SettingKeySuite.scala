package com.histmersion
package config.edit

import com.histmersion.config.testsupport.ComparedByValue.given

class SettingKeySuite extends munit.FunSuite:

  test("a key with or without histmersion. is the same key"):
    assertEquals(SettingKey.parse(SettingKey.ParseRaw("backups.keep")).map(k => k.value: String), Right("histmersion.backups.keep"))
    assertEquals(
      SettingKey.parse(SettingKey.ParseRaw(" histmersion.backups.keep ")).map(k => k.value: String),
      Right("histmersion.backups.keep")
    )
    assert(SettingKey.parse(SettingKey.ParseRaw("")).isLeft)
    assert(SettingKey.parse(SettingKey.ParseRaw("histmersion")).isLeft)
    assert(SettingKey.parse(SettingKey.ParseRaw("backups..keep")).isLeft)
    assert(SettingKey.parse(SettingKey.ParseRaw("backups.keep = 3")).isLeft)

  test("its parts"):
    val k = SettingKey.parse(SettingKey.ParseRaw("plugins.1password.enabled")).toOption.get
    assertEquals(k.relative, "plugins.1password.enabled")
    assertEquals(k.leaf, "enabled")
    assertEquals(k.parent.map(p => p.value: String), Some("histmersion.plugins.1password"))
    assert(k.within(SettingKey.parse(SettingKey.ParseRaw("plugins")).toOption.get))
    assert(!k.within(SettingKey.parse(SettingKey.ParseRaw("plugin")).toOption.get))
    assertEquals(SettingKey.fromHocon(k.hoconPath).map(p => p.value: String), Some(k.value: String))
