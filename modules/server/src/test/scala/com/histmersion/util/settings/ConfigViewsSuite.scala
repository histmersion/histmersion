package com.histmersion
package util.settings

import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.config.UserConfigFile

import java.nio.file.Path

class ConfigViewsSuite extends munit.FunSuite:

  test("the config file's line: its path as text, whether it was read, the keys it ignored"):
    val f = UserConfigFile(
      Some(Path.of("/c/histmersion.conf")),
      loaded = UserConfigFile.Loaded(true),
      List(UserConfigFile.UnknownKeys("histmersion.x"))
    )
    assertEquals(
      f.info,
      ConfigFileInfo(
        Some(ConfigFileInfo.Path("/c/histmersion.conf")),
        loaded = ConfigFileInfo.Loaded(true),
        List(ConfigFileInfo.UnknownKeys("histmersion.x"))
      )
    )
    assertEquals(
      UserConfigFile(None, loaded = UserConfigFile.Loaded(false)).info,
      ConfigFileInfo(None, loaded = ConfigFileInfo.Loaded(false), Nil)
    )
