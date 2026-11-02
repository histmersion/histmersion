package com.histmersion.util.settings

import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.config.UserConfigFile

import java.nio.file.Path

class ConfigViewsSuite extends munit.FunSuite:

  test("the config file's line: its path as text, whether it was read, the keys it ignored"):
    val f = UserConfigFile(Some(Path.of("/c/histmersion.conf")), loaded = true, List("histmersion.x"))
    assertEquals(f.info, ConfigFileInfo(Some("/c/histmersion.conf"), loaded = true, List("histmersion.x")))
    assertEquals(UserConfigFile(None, loaded = false).info, ConfigFileInfo(None, loaded = false, Nil))
