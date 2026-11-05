package com.histmersion.pluginresolver

import java.nio.file.Path

class ResolutionSuite extends munit.FunSuite:

  private def c(s: String): ModuleCoordinates = ModuleCoordinates.parse(_root_.com.histmersion.pluginresolver.ModuleCoordinates.ParseRaw(s)).fold(e => fail(e), identity)
  private def k(s: String): ModuleKey = ModuleKey.parse(_root_.com.histmersion.pluginresolver.ModuleKey.ParseRaw(s)).fold(e => fail(e), identity)

  private val server = ServerModules
    .parse(
      List(_root_.com.histmersion.pluginresolver.Resolver.ManifestLine("io.circe:circe-core_3:0.14.17"), _root_.com.histmersion.pluginresolver.Resolver.ManifestLine("org.ekrich:sconfig_3:2.1.0"), _root_.com.histmersion.pluginresolver.Resolver.ManifestLine("# a comment"), _root_.com.histmersion.pluginresolver.Resolver.ManifestLine("")),
      List(_root_.com.histmersion.pluginresolver.Resolver.ManifestLine("io.github.cquiroz:scala-java-time-tzdb_3"))
    )
    .fold(e => fail(e), identity)

  test("the kit's module list parses; a malformed line is named"):
    assertEquals(server.shipped.get(k("io.circe:circe-core_3")).map(_.value: String), Some("0.14.17"))
    assert(server.provides(k("io.github.cquiroz:scala-java-time-tzdb_3")))
    assert(!server.provides(k("org.scala-lang.modules:scala-xml_3")))
    val bad = ServerModules.parse(List(_root_.com.histmersion.pluginresolver.Resolver.ManifestLine("io.circe:circe-core_3")), Nil)
    assert(bad.left.exists(_.contains("io.circe:circe-core_3")), bad.toString)

  test("a plugin that needs a NEWER version of a shipped module conflicts, naming the module and both versions"):
    val got = Resolution.conflicts(
      _root_.com.histmersion.pluginresolver.Resolver.FileName("p-1.0.jar"),
      List(c("io.circe:circe-core_3:0.14.20"), c("org.scala-lang.modules:scala-xml_3:2.4.0")),
      server,
      Coursier.order
    )
    assertEquals(
      got.map(x => (x.plugin, x.key.toString, x.wanted.value: String, x.shipped.value: String)),
      List(("p-1.0.jar", "io.circe:circe-core_3", "0.14.20", "0.14.17"))
    )
    assert(got.head.message.contains("p-1.0.jar needs io.circe:circe-core_3 0.14.20"), got.head.message)
    assert(got.head.message.contains("ships 0.14.17"), got.head.message)

  test("an older or the same version of a shipped module is no conflict: the server's is chosen"):
    assertEquals(
      Resolution.conflicts(
        _root_.com.histmersion.pluginresolver.Resolver.FileName("p.jar"),
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
      Map(k("org.scala-lang.modules:scala-xml_3") -> List(_root_.com.histmersion.pluginresolver.Resolver.FileName("plugin-1.0.jar")))
    )
    assertEquals(
      deps.map(d => (d.coordinates.toString, d.libName, d.neededBy)),
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
    val got = Resolution.neededBy(List({ val refactorTuple = "a.jar" -> List(c("x:y_3:1"), c("x:z_3:1")); (_root_.com.histmersion.pluginresolver.Resolver.FileName(refactorTuple._1), refactorTuple._2) }, { val refactorTuple2 = "b.jar" -> List(c("x:y_3:2")); (_root_.com.histmersion.pluginresolver.Resolver.FileName(refactorTuple2._1), refactorTuple2._2) }))
    assertEquals(got, Map(k("x:y_3") -> List("a.jar", "b.jar"), k("x:z_3") -> List("a.jar")))

  test("the record files: class-path order, and inputs sorted by name"):
    val deps = List(Dependency(c("x:y_3:1"), Path.of("/c/y_3-1.jar"), Nil))
    assertEquals(Resolution.classpathFile(deps), "x-y_3-1.jar\n")
    assertEquals(Resolution.inputsFile(List({ val refactorTuple3 = "b.pom" -> "22"; (_root_.com.histmersion.pluginresolver.Resolver.FileName(refactorTuple3._1), _root_.com.histmersion.pluginresolver.Resolver.Sha256(refactorTuple3._2)) }, { val refactorTuple4 = "a.jar" -> "11"; (_root_.com.histmersion.pluginresolver.Resolver.FileName(refactorTuple4._1), _root_.com.histmersion.pluginresolver.Resolver.Sha256(refactorTuple4._2)) })), "11  a.jar\n22  b.pom\n")

  test("the report: a line per dependency and one for what the server provides"):
    val deps = List(Dependency(c("x:y_3:1"), Path.of("/c/y_3-1.jar"), List(_root_.com.histmersion.pluginresolver.Resolver.FileName("p.jar"))))
    assertEquals(
      Resolution.report(deps, List(k("io.circe:circe-core_3"))),
      List("dependency: x:y_3:1 (for p.jar)", "provided by Histmersion: 1 module(s) (io.circe:circe-core_3)")
    )

  test("repository arguments: URLs as given, a directory as its file: URL"):
    assertEquals(
      RepositoryUrl.fromArgument(_root_.com.histmersion.pluginresolver.RepositoryUrl.FromArgumentRaw("https://repo.example.org/m2")).map(_.value: String),
      Right("https://repo.example.org/m2")
    )
    assert(RepositoryUrl.fromArgument(_root_.com.histmersion.pluginresolver.RepositoryUrl.FromArgumentRaw("/tmp/repo")).exists(_.value.startsWith("file:/")))
