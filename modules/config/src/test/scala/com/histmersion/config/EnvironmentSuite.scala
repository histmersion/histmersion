package com.histmersion
package config

import org.ekrich.config.ConfigFactory
import com.histmersion.config.testsupport.ComparedByValue.given

import java.nio.file.Path
import scala.jdk.CollectionConverters.*

class EnvironmentSuite extends munit.FunSuite:

  private val env = Environment(
    Map(
      Environment.VariablesKey("HOME") -> Environment.VariablesValue("/home/ada"),
      Environment.VariablesKey("XDG_CACHE_HOME") -> Environment.VariablesValue(" "),
      Environment.VariablesKey("HISTMERSION_BIND") -> Environment.VariablesValue(" 0.0.0.0 ")
    )
  )

  test("a variable is read as set; a blank one is no value"):
    assertEquals(env.get(Environment.VariablesKey("HOME")), Some("/home/ada"))
    assertEquals(env.nonBlank(Environment.VariablesKey("HISTMERSION_BIND")), Some("0.0.0.0"))
    assertEquals(env.nonBlank(Environment.VariablesKey("XDG_CACHE_HOME")), None)
    assertEquals(env.get(Environment.VariablesKey("NOPE")), None)

  test("the environment of a load holds what the load read"):
    val loaded = Environment.of(ConfigFactory.parseMap(Map("HOME" -> "/home/ada", "N" -> "3").asJava))
    assertEquals(
      loaded,
      Environment(
        Map(
          Environment.VariablesKey("HOME") -> Environment.VariablesValue("/home/ada"),
          Environment.VariablesKey("N") -> Environment.VariablesValue("3")
        )
      )
    )

  test("the paths of one environment are the pure resolver's"):
    val paths = UserPaths(env, UserPaths.OsName("Linux"))
    assertEquals(paths.dataDir(RuntimeConfig.DataDir("")), Some(Path.of("/home/ada/.local/share/histmersion")))
    assertEquals(paths.dataDir(RuntimeConfig.DataDir("/srv/h")), Some(Path.of("/srv/h")))
    assertEquals(paths.cacheDir, Some(Path.of("/home/ada/.cache/histmersion")))
    assertEquals(paths.configFile, Some(Path.of("/home/ada/.config/histmersion/histmersion.conf")))
    assertEquals(
      UserPaths(env, UserPaths.OsName("Mac OS X")).defaultDataDir,
      Some(Path.of("/home/ada/Library/Application Support/Histmersion"))
    )
    assertEquals(UserPaths(Environment.empty, UserPaths.OsName("Linux")).dataDir(RuntimeConfig.DataDir("")), None)
