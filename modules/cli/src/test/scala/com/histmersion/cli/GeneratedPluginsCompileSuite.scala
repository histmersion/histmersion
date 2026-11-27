package com.histmersion
package cli

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.cli.{Cli, ExitCode}
import com.histmersion.lint.{tolerated, LintRule}

/** The plugin projects `histmersion plugin new` writes, compiled against the plugin API of THIS build: a template that
  * names a type, a constructor or a method the SPI no longer has fails here, offline, in the normal test run. The main
  * sources are compiled against the plugin API, the test sources also against the plugin testkit, with the Scala
  * compiler the build uses; the class path is this test's own, which holds both.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class GeneratedPluginsCompileSuite extends munit.FunSuite:

  override val munitTimeout = scala.concurrent.duration.Duration(5, "min")

  private val repo = TemplateRepository.MavenLocal

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private def request(kind: PluginKind) =
    val pid = PluginId.parse("my-thing").toOption.get
    PluginTemplate.Request(
      pid,
      kind,
      ScalaPackage.parse(pid.packageName.value).toOption.get,
      repo,
      PluginTemplate.Request.AgentNotes.WithoutNotes
    )

  /** The sources of the project, written under a fresh directory: `main` and `test`. */
  private def written(kind: PluginKind): (Path, List[Path], List[Path]) =
    val dir = Files.createTempDirectory("generated-plugin")
    val paths = PluginAuthoring
      .write(dir, PluginTemplate.files(request(kind)))
      .left
      .map(Cli.Error(_))
      .fold(e => fail(e.toString), identity)
    val (tests, mains) = paths
      .filter(_.toString.endsWith(".scala"))
      .partition { p =>
        val relative = dir.relativize(p).toString
        relative.contains("Suite") || relative.startsWith("test/")
      }
    (dir, mains, tests)

  /** Compile `sources`; the compiler's messages when it fails, empty when it succeeds. */
  private def compile(sources: List[Path], classpath: String, out: Path): String =
    val reporter = new StringBuilder
    val args = Array("-classpath", classpath, "-d", out.toString, "-language:strictEquality", "-color:never") ++
      sources.map(_.toString)
    val reported = dotty.tools.dotc.Main.process(args)
    if reported.hasErrors then reported.toString + " errors: " + sources.mkString(", ") else ""

  PluginKind.values.foreach { kind =>
    test(s"${kind.name}: the generated sources compile against the plugin API and testkit"):
      val (dir, mains, tests) = written(kind)
      val classes = Files.createDirectories(dir.resolve("classes"))
      val classpath = PluginTestClasspath.entries.mkString(java.io.File.pathSeparator)
      val problems = compile(mains ++ tests, classpath, classes)
      assertEquals(problems, "", s"the generated ${kind.name} project does not compile")
  }
