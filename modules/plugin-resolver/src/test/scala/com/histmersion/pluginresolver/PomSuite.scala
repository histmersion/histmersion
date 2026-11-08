package com.histmersion
package pluginresolver

import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}

class PomSuite extends munit.FunSuite:

  private def pom(body: String) =
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>$body</project>""".stripMargin

  test("a POM's own coordinates"):
    assertEquals(
      Pom
        .coordinates(
          PluginArtifact.Pom(pom("<groupId>com.example</groupId><artifactId>p_3</artifactId><version>1.0</version>"))
        )
        .map(_.toString),
      Right("com.example:p_3:1.0")
    )

  test("a group and version the POM does not state are its parent's"):
    val text = pom(
      "<parent><groupId>org.parent</groupId><artifactId>base</artifactId><version>2.0</version></parent><artifactId>child</artifactId>"
    )
    assertEquals(Pom.coordinates(PluginArtifact.Pom(text)).map(_.toString), Right("org.parent:child:2.0"))

  test("a dependency's coordinates are not the POM's"):
    val text = pom(
      "<artifactId>p</artifactId><dependencies><dependency><groupId>x</groupId><version>9</version></dependency></dependencies>"
    )
    assert(Pom.coordinates(PluginArtifact.Pom(text)).isLeft)

  test("no DOCTYPE: a POM never makes the parser read another file"):
    val text =
      """<?xml version="1.0"?><!DOCTYPE p [<!ENTITY e SYSTEM "file:///etc/passwd">]><project><groupId>&e;</groupId></project>"""
    assert(Pom.coordinates(PluginArtifact.Pom(text)).left.exists((_).value.startsWith("not a POM")))

  test("the POM beside the jar first, else the one inside it, unless it holds several"):
    val dir = Files.createTempDirectory("pom-suite")
    def jar(name: String, poms: List[String]): Path =
      val p = dir.resolve(name)
      val out = ZipOutputStream(Files.newOutputStream(p))
      try
        poms.zipWithIndex.foreach { (text, i) =>
          out.putNextEntry(ZipEntry(s"META-INF/maven/g/a$i/pom.xml")); out.write(text.getBytes); out.closeEntry()
        }
      finally out.close()
      p
    val inside = pom("<groupId>g</groupId><artifactId>inside</artifactId><version>1</version>")
    val one = jar("one-1.0.jar", List(inside))
    assertEquals(((Pom.of(one)).map(refactorValue => (refactorValue).value): Option[String]), Some(inside))
    Files.writeString(dir.resolve("one-1.0.pom"), "beside")
    assertEquals(((Pom.of(one)).map(refactorValue2 => (refactorValue2).value): Option[String]), Some("beside"))
    assertEquals(Pom.of(jar("shaded-1.0.jar", List(inside, inside))), None)
    assertEquals(Pom.of(jar("bare-1.0.jar", Nil)), None)
