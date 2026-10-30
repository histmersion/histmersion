package com.histmersion.server.plugins

import java.nio.charset.StandardCharsets.UTF_8

/** The plugin platform is part of the plugin compatibility contract: the libraries Histmersion provides to plugins, at
  * the versions it ships (modules/plugin-api/plugin-platform.txt). The build resolves the same list against the
  * server's class path; this suite fails when the two differ, so that a dependency upgrade that changes what plugins
  * compile against is seen and acknowledged by updating the file.
  */
class PluginPlatformSuite extends munit.FunSuite:

  private def entries(resource: String): List[String] =
    val in = Option(getClass.getClassLoader.getResourceAsStream(resource)).getOrElse(fail(s"no resource $resource"))
    String(in.readAllBytes(), UTF_8).linesIterator.map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#")).toList

  private def versions(lines: List[String]): Map[String, String] =
    lines.map(l => l.substring(0, l.lastIndexOf(':')) -> l.substring(l.lastIndexOf(':') + 1)).toMap

  test("the platform's libraries ship at the versions the contract file names"):
    val contract = versions(entries("plugin-platform-contract.txt"))
    val build = versions(entries("plugin-platform-build.txt"))
    val changed = contract.toList.sorted.collect {
      case (k, v) if build.get(k).exists(_ != v) => s"  $k: $v → ${build(k)}"
    }
    assert(
      changed.isEmpty,
      "the plugin platform changed — plugins built against the old versions may stop linking:\n" + changed.mkString(
        "\n"
      ) +
        "\nIf the upgrade is meant, write the new versions into modules/plugin-api/plugin-platform.txt (and name the change in the release notes)."
    )

  test("every library of the platform is on the server's class path"):
    val missing = versions(entries("plugin-platform-build.txt")).collect { case (k, "NOT-SHIPPED") => k }.toList.sorted
    assert(
      missing.isEmpty,
      s"no longer shipped by the server: ${missing.mkString(", ")} — remove it from modules/plugin-api/plugin-platform.txt (a contract change) or ship it again"
    )

  test("the contract file names each library once, as group:artifact:version"):
    val lines = entries("plugin-platform-contract.txt")
    assert(lines.forall(_.split(':').length == 3), lines.filterNot(_.split(':').length == 3).mkString(", "))
    val keys = lines.map(l => l.substring(0, l.lastIndexOf(':')))
    assertEquals(keys.diff(keys.distinct), Nil)
