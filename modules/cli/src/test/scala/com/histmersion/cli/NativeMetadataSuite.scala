package com.histmersion.cli

import scala.jdk.CollectionConverters.*

/** This module's native-image metadata is the single JDK-25 `reachability-metadata.json`. A legacy split file at the
  * ROOT of META-INF/native-image can only come from this module (dependency jars nest theirs under <group>/<artifact>/)
  * and means an older JDK's tracing agent was run — see CLAUDE.md, "The tracing-agent rule". `nativeImage` refuses to
  * build in that state too.
  */
class NativeMetadataSuite extends munit.FunSuite:

  private val legacy = List(
    "jni-config.json",
    "predefined-classes-config.json",
    "proxy-config.json",
    "reflect-config.json",
    "resource-config.json",
    "serialization-config.json"
  )

  private def onClasspath(name: String): List[String] =
    getClass.getClassLoader.getResources(s"META-INF/native-image/$name").asScala.map(_.toString).toList

  test("reachability-metadata.json is present"):
    assert(onClasspath("reachability-metadata.json").nonEmpty)

  test("no legacy split metadata beside it"):
    assertEquals(legacy.flatMap(onClasspath), Nil)
