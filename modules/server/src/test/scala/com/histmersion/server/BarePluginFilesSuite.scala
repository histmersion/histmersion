package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

import com.histmersion.util.plugins.PluginKit
import Supervisor.{rewrittenOnce, PluginFile}

/** The once-only rewrite, at the server's start, of the plugin files an earlier version wrote with a bare problem: on
  * files in a temporary directory, never the install's.
  */
class BarePluginFilesSuite extends munit.FunSuite:

  private val dir = FunFixture[Path](_ => Files.createTempDirectory("plugin-rewrite"), d => delete(d))

  private def delete(d: Path): Unit =
    Files.walk(d).iterator.asScala.toList.reverse.foreach(Files.deleteIfExists(_))

  private def names(d: Path): List[String] = Files.list(d).iterator.asScala.map(_.getFileName.toString).toList.sorted

  private val Sha = "1" * 64

  private def index(error: String) =
    s"""{"jars":[{"file":"p-1.0.jar","sha256":"$Sha","source":"maven:org.example:p:1.0","addedAt":"2026-11-14T00:00:00Z","plugins":[{"id":"p","version":"1","spiVersion":1,"error":$error}]}]}"""

  dir.test("a bare problem becomes a list of one, with nothing else changed, and no other file is left"): d =>
    val file = d.resolve("index.json")
    Files.writeString(file, index("\"it needs a key\""), UTF_8)
    assertEquals(file.rewrittenOnce(PluginKit.parseIndex), Right(PluginFile.Rewritten))
    val now = Files.readString(file, UTF_8)
    assert(now.replaceAll("\\s", "").contains("\"error\":[\"itneedsakey\"]"), now)
    assertEquals(
      PluginKit.parseIndex(now).map(_.jars.flatMap(_.plugins.map(p => (s"${p.id}", s"${p.version}")))),
      Right(List("p" -> "1"))
    )
    assertEquals(names(d), List("index.json"))
    assertEquals(file.rewrittenOnce(PluginKit.parseIndex), Right(PluginFile.Kept), "only once")

  dir.test("a file in the list form is not written"): d =>
    val file = d.resolve("index.json")
    val text = index("[\"one\"]")
    Files.writeString(file, text, UTF_8)
    val before = Files.getLastModifiedTime(file)
    assertEquals(file.rewrittenOnce(PluginKit.parseIndex), Right(PluginFile.Kept))
    assertEquals(Files.readString(file, UTF_8), text)
    assertEquals(Files.getLastModifiedTime(file), before)

  dir.test("a file that does not parse, or would not read once rewritten, is left exactly as it is"): d =>
    val broken = d.resolve("index.json")
    Files.writeString(broken, "{\"jars\": [", UTF_8)
    assert(broken.rewrittenOnce(PluginKit.parseIndex).isLeft)
    assertEquals(Files.readString(broken, UTF_8), "{\"jars\": [")
    val strange = d.resolve("plugin-set.json")
    Files.writeString(strange, index("\"bare\""), UTF_8)
    assert(strange.rewrittenOnce(PluginKit.parseSet).isLeft, "an index is no plugin set")
    assertEquals(Files.readString(strange, UTF_8), index("\"bare\""))
    assertEquals(names(d), List("index.json", "plugin-set.json"))

  dir.test("a missing file is nothing to rewrite"): d =>
    assertEquals(d.resolve("index.json").rewrittenOnce(PluginKit.parseIndex), Right(PluginFile.Kept))
