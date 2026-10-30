package com.histmersion.server

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

  /** This module's copy: its classes directory in sbt's own JVM, its jar (`histmersion-server_3-….jar`) in a forked
    * test JVM — never a dependency's.
    */
  private def own(name: String): List[String] =
    onClasspath(name).filter(u => !u.contains(".jar!") || u.contains("/histmersion-server_"))

  test("reachability-metadata.json is present"):
    assert(onClasspath("reachability-metadata.json").nonEmpty)

  test("no legacy split metadata beside it"):
    assertEquals(legacy.flatMap(onClasspath), Nil)

  /** #820: the native server only bundles resources its metadata names, so a migration missing from it is invisible to
    * Flyway at runtime ("NullPointerException at Migrate$Res.read" on start, which only the bundle smoke test caught).
    * Every file the build lists in db/migration/index.txt must have its own resource entry in THIS module's metadata.
    */
  test("every migration is a resource in the native-image metadata"):
    val loader = getClass.getClassLoader
    val index = scala.io.Source.fromInputStream(loader.getResourceAsStream("db/migration/index.txt"), "UTF-8")
    val migrations = try index.getLines().map(_.trim).filter(_.endsWith(".sql")).toList
    finally index.close()
    assert(migrations.nonEmpty, "db/migration/index.txt lists no migrations")
    val ours = own("reachability-metadata.json")
    assertEquals(ours.size, 1, s"expected this module's own metadata file, found: $ours")
    val metadata = scala.io.Source.fromURL(ours.head, "UTF-8").mkString
    val globs = "\"glob\"\\s*:\\s*\"([^\"]+)\"".r.findAllMatchIn(metadata).map(_.group(1)).toSet
    val missing = migrations.filterNot(m => globs.contains(s"db/migration/$m"))
    assertEquals(
      missing,
      Nil,
      "add each to modules/server/src/main/resources/META-INF/native-image/reachability-metadata.json " +
        "as {\"glob\": \"db/migration/<file>\"}"
    )

  /** The same rule for the embedded agents' prompt files: a prompt the metadata does not name is missing from the
    * native image, and the step that needs it fails at run time.
    */
  test("every bundled agent prompt is a resource in the native-image metadata"):
    val ours = own("reachability-metadata.json")
    val metadata = scala.io.Source.fromURL(ours.head, "UTF-8").mkString
    val globs = "\"glob\"\\s*:\\s*\"([^\"]+)\"".r.findAllMatchIn(metadata).map(_.group(1)).toSet
    val bundled = com.histmersion.server.agents.Prompts.bundled
    assert(bundled.nonEmpty, "agents/index.txt lists nothing")
    assertEquals(("index.txt" :: bundled).map(p => s"agents/$p").filterNot(globs), Nil)
