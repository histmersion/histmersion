package com.histmersion.plugin.testkit

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, FileSystems, Path}
import scala.jdk.CollectionConverters.*

/** The testkit's public API is part of the plugin compatibility contract, as plugin-api is: plugins' tests compile and
  * link against it. modules/plugin-testkit/plugin-testkit-api.txt lists every public class, constructor and method of
  * `com.histmersion.plugin.testkit` (not `internal`); this suite fails when the compiled testkit differs, so a change
  * to that surface is seen and acknowledged by updating the file (`HISTMERSION_UPDATE_TESTKIT_API=1` rewrites it) and
  * named in the release notes.
  */
class TestkitApiSuite extends munit.FunSuite:

  private val contract = Path.of("modules/plugin-testkit/plugin-testkit-api.txt")
  private val Package = "com.histmersion.plugin.testkit."

  private def surface: List[String] =
    val location = Path.of(classOf[PluginHarness].getProtectionDomain.getCodeSource.getLocation.toURI)
    val root = if Files.isDirectory(location) then location else FileSystems.newFileSystem(location).getPath("/")
    val names = Files
      .walk(root)
      .iterator
      .asScala
      .map(p => root.relativize(p).toString.replace('\\', '/'))
      .filter(n => n.endsWith(".class") && n.startsWith(Package.replace('.', '/')))
      .map(_.stripSuffix(".class").replace('/', '.'))
      .filterNot(n => n.startsWith(Package + "internal.") || n.contains("$anon"))
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

  test("the testkit's public API is the one the contract file lists"):
    val now = surface
    if sys.env.get("HISTMERSION_UPDATE_TESTKIT_API").contains("1") then
      Files.writeString(
        contract,
        ("# The plugin testkit's public API (TestkitApiSuite): one line per class, constructor and method.\n" +
          "# A changed line is a changed contract, to be named in the release notes.\n" + now.mkString("", "\n", "\n")),
        UTF_8
      )
    val listed =
      Files.readAllLines(contract, UTF_8).asScala.toList.map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#"))
    val added = now.diff(listed)
    val removed = listed.diff(now)
    assert(
      added.isEmpty && removed.isEmpty,
      "the plugin testkit's public API changed — plugins' tests built against it may stop compiling or linking:\n" +
        added.map("  + " + _).mkString("\n") + "\n" + removed.map("  - " + _).mkString("\n") +
        "\nIf the change is meant, run with HISTMERSION_UPDATE_TESTKIT_API=1 and name it in the release notes."
    )
