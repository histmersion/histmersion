package com.histmersion.config

import org.ekrich.config.ConfigFactory

import java.nio.file.Path
import scala.jdk.CollectionConverters.*

class EnvironmentSuite extends munit.FunSuite:

  private val env = Environment(Map("HOME" -> "/home/ada", "XDG_CACHE_HOME" -> " ", "HISTMERSION_BIND" -> " 0.0.0.0 "))

  test("a variable is read as set; a blank one is no value"):
    assertEquals(env.get("HOME"), Some("/home/ada"))
    assertEquals(env.nonBlank("HISTMERSION_BIND"), Some("0.0.0.0"))
    assertEquals(env.nonBlank("XDG_CACHE_HOME"), None)
    assertEquals(env.get("NOPE"), None)

  test("the environment of a load holds what the load read"):
    val loaded = Environment.of(ConfigFactory.parseMap(Map("HOME" -> "/home/ada", "N" -> "3").asJava))
    assertEquals(loaded, Environment(Map("HOME" -> "/home/ada", "N" -> "3")))

  test("the paths of one environment are the pure resolver's"):
    val paths = UserPaths(env, "Linux")
    assertEquals(paths.dataDir(""), Some(Path.of("/home/ada/.local/share/histmersion")))
    assertEquals(paths.dataDir("/srv/h"), Some(Path.of("/srv/h")))
    assertEquals(paths.cacheDir, Some(Path.of("/home/ada/.cache/histmersion")))
    assertEquals(paths.configFile, Some(Path.of("/home/ada/.config/histmersion/histmersion.conf")))
    assertEquals(UserPaths(env, "Mac OS X").defaultDataDir, Some(Path.of("/home/ada/Library/Application Support/Histmersion")))
    assertEquals(UserPaths(Environment.empty, "Linux").dataDir(""), None)
