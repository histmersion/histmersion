package com.histmersion
package pluginresolver

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.{sentence, sentences}

import java.nio.file.Path
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ResolutionSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def c(s: String): ModuleCoordinates =
    ModuleCoordinates.parse(ModuleCoordinates.Text(s)).fold(e => fail(e.sentences.mkString("; ")), identity)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def k(s: String): ModuleKey =
    ModuleKey.parse(ModuleKey.Text(s)).fold(e => fail(e.sentences.mkString("; ")), identity)

  private val server = ServerModules
    .parse(
      List(
        Resolver.ManifestLine("io.circe:circe-core_3:0.14.17"),
        Resolver.ManifestLine("org.ekrich:sconfig_3:2.1.0"),
        Resolver.ManifestLine("# a comment"),
        Resolver.ManifestLine("")
      ),
      List(Resolver.ManifestLine("io.github.cquiroz:scala-java-time-tzdb_3"))
    )
    .fold(e => fail(e.sentences.mkString("; ")), identity)

  test("the kit's module list parses; a malformed line is named"):
    assertEquals(server.shipped.get(k("io.circe:circe-core_3")).map(_.value: String), Some("0.14.17"))
    assert(server.provides(k("io.github.cquiroz:scala-java-time-tzdb_3")))
    assert(!server.provides(k("org.scala-lang.modules:scala-xml_3")))
    val bad = ServerModules.parse(List(Resolver.ManifestLine("io.circe:circe-core_3")), Nil)
    bad.left.map(_.toVector) match
      case Left(Vector(Problem.Invalid.Malformed(t, _, raw, _))) =>
        assertEquals(
          (s"$t", s"$raw"),
          ("ModuleCoordinates.Written", "io.circe:circe-core_3")
        )
      case other => fail(s"not malformed: $other")

  test("a plugin that needs a NEWER version of a shipped module conflicts, naming the module and both versions"):
    val got = Resolution.conflicts(
      Resolver.FileName("p-1.0.jar"),
      List(c("io.circe:circe-core_3:0.14.20"), c("org.scala-lang.modules:scala-xml_3:2.4.0")),
      server,
      Coursier.order
    )
    assertEquals(
      ((got.map(x => (x.plugin, x.key.toString, x.wanted.value: String, x.shipped.value: String))).map {
        refactorValue =>
          val refactorTuple5 = refactorValue;
          ((refactorTuple5._1).value, refactorTuple5._2, refactorTuple5._3, refactorTuple5._4)
      }: List[(String, String, String, String)]),
      List(("p-1.0.jar", "io.circe:circe-core_3", "0.14.20", "0.14.17"))
    )
    assertEquals(
      got.head.message,
      Problem.Plugin.Dependency(
        Problem.Plugin.Id("p-1.0.jar"),
        Problem.Raw("io.circe:circe-core_3"),
        Problem.Raw("0.14.20"),
        Problem.Raw("0.14.17")
      )
    )
    assert(got.head.message.sentence.contains("p-1.0.jar needs io.circe:circe-core_3 0.14.20"), got.head.message)
    assert(got.head.message.sentence.contains("but this Histmersion ships 0.14.17"), got.head.message)

  test("an older or the same version of a shipped module is no conflict: the server's is chosen"):
    assertEquals(
      Resolution.conflicts(
        Resolver.FileName("p.jar"),
        List(c("io.circe:circe-core_3:0.14.10"), c("org.ekrich:sconfig_3:2.1.0")),
        server,
        Coursier.order
      ),
      Nil
    )

  test("Maven's version order, not the text's"):
    assert(Coursier.order(ModuleVersion("1.10.0"), ModuleVersion("1.9.0")) > 0)
    assert(Coursier.order(ModuleVersion("1.0.0-RC1"), ModuleVersion("1.0.0")) < 0)

  test("lib/ gets what is neither a plugin nor the server's, and has a jar"):
    val joint = List(
      c("com.example:plugin_3:1.0") -> Some(Path.of("/c/plugin_3-1.0.jar")),
      c("io.circe:circe-core_3:0.14.17") -> Some(Path.of("/c/circe-core_3-0.14.17.jar")),
      c("io.github.cquiroz:scala-java-time-tzdb_3:2.6.0") -> Some(Path.of("/c/tzdb.jar")),
      c("org.scala-lang.modules:scala-xml_3:2.4.0") -> Some(Path.of("/c/scala-xml_3-2.4.0.jar")),
      c("com.example:bom-only:1.0") -> None
    )
    val deps = Resolution.dependencies(
      joint,
      Set(k("com.example:plugin_3")),
      server,
      Map(k("org.scala-lang.modules:scala-xml_3") -> List(Resolver.FileName("plugin-1.0.jar")))
    )
    assertEquals(
      ((deps.map(d => (d.coordinates.toString, d.libName, d.neededBy))).map { refactorValue2 =>
        val refactorTuple6 = refactorValue2;
        (
          refactorTuple6._1,
          (refactorTuple6._2).value,
          (refactorTuple6._3).map(refactorValue3 => (refactorValue3).value)
        )
      }: List[(String, String, List[String])]),
      List(
        (
          "org.scala-lang.modules:scala-xml_3:2.4.0",
          "org.scala-lang.modules-scala-xml_3-2.4.0.jar",
          List("plugin-1.0.jar")
        )
      )
    )

  test("two groups' artifacts of the same name and version get different lib names"):
    val a = Dependency(c("com.softwaremill.ox:core_3:1.0.2"), Path.of("/c/core_3-1.0.2.jar"), Nil)
    val b = Dependency(c("com.example:core_3:1.0.2"), Path.of("/x/core_3-1.0.2.jar"), Nil)
    assertNotEquals(a.libName, b.libName)

  test("neededBy: each module, the plugins whose own resolution has it"):
    val got = Resolution.neededBy(
      List(
        {
          val refactorTuple = "a.jar" -> List(c("x:y_3:1"), c("x:z_3:1"));
          (Resolver.FileName(refactorTuple._1), refactorTuple._2)
        }, {
          val refactorTuple2 = "b.jar" -> List(c("x:y_3:2")); (Resolver.FileName(refactorTuple2._1), refactorTuple2._2)
        }
      )
    )
    assertEquals(
      ((got).iterator.map { case (refactorKey, refactorItem) =>
        (refactorKey, (refactorItem).map(refactorValue4 => (refactorValue4).value))
      }.toMap: Map[ModuleKey, List[String]]),
      Map(k("x:y_3") -> List("a.jar", "b.jar"), k("x:z_3") -> List("a.jar"))
    )

  test("the record files: class-path order, and inputs sorted by name"):
    val deps = List(Dependency(c("x:y_3:1"), Path.of("/c/y_3-1.jar"), Nil))
    assertEquals(((Resolution.classpathFile(deps)).value: String), "x-y_3-1.jar\n")
    assertEquals(
      ((Resolution
        .inputsFile(
          List(
            {
              val refactorTuple3 = "b.pom" -> "22";
              (Resolver.FileName(refactorTuple3._1), Resolver.Sha256(refactorTuple3._2))
            }, {
              val refactorTuple4 = "a.jar" -> "11";
              (Resolver.FileName(refactorTuple4._1), Resolver.Sha256(refactorTuple4._2))
            }
          )
        ))
        .value: String),
      "11  a.jar\n22  b.pom\n"
    )

  test("the report: a line per dependency and one for what the server provides"):
    val deps = List(Dependency(c("x:y_3:1"), Path.of("/c/y_3-1.jar"), List(Resolver.FileName("p.jar"))))
    assertEquals(
      ((Resolution
        .report(deps, List(k("io.circe:circe-core_3"))))
        .map(refactorValue5 => (refactorValue5).value): List[String]),
      List("dependency: x:y_3:1 (for p.jar)", "provided by Histmersion: 1 module(s) (io.circe:circe-core_3)")
    )

  test("repository arguments: URLs as given, a directory as its file: URL"):
    assertEquals(
      RepositoryUrl.fromArgument(Resolver.Argument("https://repo.example.org/m2")).map(_.value: String),
      Right("https://repo.example.org/m2")
    )
    assert(RepositoryUrl.fromArgument(Resolver.Argument("/tmp/repo")).exists(_.value.startsWith("file:/")))
