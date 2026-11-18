package com.histmersion
package server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The versions live in one place, project/Dependencies.scala; the files that cannot read it, because a tool reads them
  * before sbt runs or a person reads them, must name the same ones.
  */
class BuildVersionsSuite extends munit.FunSuite:

  private def lines(path: String): List[String] = Files.readAllLines(Path.of(path), UTF_8).asScala.toList

  test("the versions file is key=value lines; blank lines and comments are skipped"):
    assertEquals(
      BuildVersions.parse(List("# a comment", "", "scala=3.0.0", " scalafix = 0.1.0 ", "no value")),
      Map("scala" -> "3.0.0", "scalafix" -> "0.1.0")
    )

  test("the build's versions reach the tests"):
    val v = BuildVersions.build
    List(v.scala, v.scalafix, v.scalafixScala)
      .foreach(v => assert(v.matches("""\d+\.\d+\.\d+"""), v))

  test(".sdkmanrc names the build's Scala and the sbt of project/build.properties"):
    val sdkman = BuildVersions.parse(lines(".sdkmanrc"))
    val sbt = BuildVersions.parse(lines("project/build.properties"))
    assertEquals(sdkman.get("scala"), Some(BuildVersions.build.scala))
    assertEquals(sdkman.get("sbt"), sbt.get("sbt.version"))

  test("the plugin developer's guide names the server's Scala version wherever it names one"):
    val text = lines("docs/user-guide/plugins/developing.md")
    val named =
      List("""//> using scala (\S+)""".r, """scalaVersion := "([^"]+)"""".r, """\((\S+) in this release\)""".r)
        .flatMap(r => text.flatMap(l => r.findAllMatchIn(l).map(_.group(1))))
    assertEquals(named.size, 3, "the guide names the Scala version in three places")
    assertEquals(named.distinct, List(BuildVersions.build.scala))
