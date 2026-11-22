package com.histmersion
package plugin

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, FileSystems, Path}
import scala.jdk.CollectionConverters.*

class SpiSurfaceSuite extends munit.FunSuite:

  private val contract = Path.of("modules/plugin-api/plugin-api-api.txt")
  private val Package = "com.histmersion.plugin."
  private val VersionLine = "spi-version "

  private def surface: List[String] =
    val location = Path.of(classOf[Fetch].getProtectionDomain.getCodeSource.getLocation.toURI)
    val root = if Files.isDirectory(location) then location else FileSystems.newFileSystem(location).getPath("/")
    val names = Files
      .walk(root)
      .iterator
      .asScala
      .map(p => root.relativize(p).toString.replace('\\', '/'))
      .filter(n => n.endsWith(".class") && n.startsWith(Package.replace('.', '/')))
      .map(_.stripSuffix(".class").replace('/', '.'))
      .filterNot(_.contains("$anon"))
      .toList
    def shown(c: Class[?]): String = c.getTypeName
    names
      .map(Class.forName(_, false, getClass.getClassLoader))
      .filter(c => java.lang.reflect.Modifier.isPublic(c.getModifiers))
      .flatMap { c =>
        val ctors =
          c.getConstructors.toList.map(k => s"${c.getName} new(${k.getParameterTypes.map(shown).mkString(", ")})")
        val methods = c.getDeclaredMethods.toList
          .filter(m =>
            java.lang.reflect.Modifier.isPublic(m.getModifiers) && !m.isSynthetic && !m.getName.contains("$")
          )
          .map(m =>
            s"${c.getName} ${m.getName}(${m.getParameterTypes.map(shown).mkString(", ")}): ${shown(m.getReturnType)}"
          )
        s"${c.getName} class" :: ctors ++ methods
      }
      .distinct
      .sorted

  private def listed: (Option[Int], List[String]) =
    val lines =
      if !Files.exists(contract) then Nil
      else
        Files.readAllLines(contract, UTF_8).asScala.toList.map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#"))
    (
      lines.collectFirst { case l if l.startsWith(VersionLine) => l.drop(VersionLine.length).trim.toIntOption }.flatten,
      lines.filterNot(_.startsWith(VersionLine))
    )

  test("the SPI's surface is the one the contract file lists, under the version it names"):
    val now = surface
    val (version, before) = listed
    val changed = now != before
    if sys.env.get("HISTMERSION_UPDATE_PLUGIN_API").contains("1") then
      assert(
        !changed || !version.contains(Spi.Version),
        s"the SPI's surface changed but Spi.Version is still ${Spi.Version}: bump it first"
      )
      Files.writeString(
        contract,
        "# The plugin SPI's public surface (SpiSurfaceSuite): the SPI version, then one line per class, constructor and\n" +
          "# method. A changed line needs a new Spi.Version.\n" + s"$VersionLine${Spi.Version}\n" +
          now.mkString("", "\n", "\n"),
        UTF_8
      )
    else
      val added = now.diff(before)
      val removed = before.diff(now)
      assert(
        added.isEmpty && removed.isEmpty,
        "the plugin SPI's surface changed: bump Spi.Version and run with HISTMERSION_UPDATE_PLUGIN_API=1:\n" +
          added.map("  + " + _).mkString("\n") + "\n" + removed.map("  - " + _).mkString("\n")
      )
      assertEquals(version, Some(Spi.Version), "the contract file names another SPI version than Spi.Version")
