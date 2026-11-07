package com.histmersion.util.settings

import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.config.UserConfigFile

import java.nio.file.Path

class ConfigViewsSuite extends munit.FunSuite:

  test("the config file's line: its path as text, whether it was read, the keys it ignored"):
    val f = UserConfigFile(Some(Path.of("/c/histmersion.conf")), loaded = com.histmersion.config.UserConfigFile.Loaded(true), List(com.histmersion.config.UserConfigFile.UnknownKeys("histmersion.x")))
    assertEquals(f.info, ConfigFileInfo(Some(com.histmersion.api.settings.ConfigFileInfo.Path("/c/histmersion.conf")), loaded = com.histmersion.api.settings.ConfigFileInfo.Loaded(true), List(com.histmersion.api.settings.ConfigFileInfo.UnknownKeys("histmersion.x"))))
    assertEquals(UserConfigFile(None, loaded = com.histmersion.config.UserConfigFile.Loaded(false)).info, ConfigFileInfo(None, loaded = com.histmersion.api.settings.ConfigFileInfo.Loaded(false), Nil))
