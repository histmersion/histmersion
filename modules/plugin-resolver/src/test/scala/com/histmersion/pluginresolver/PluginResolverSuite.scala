package com.histmersion
package pluginresolver

import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.{sentence, sentences}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** `resolve` end to end, offline: the test plugin `hello-deps` (its POM lists `hello-lib`) from the fixture repository
  * the build writes, a kit whose module list is the test's, a coursier cache of its own. The Scala library is in the
  * kit's excludes, so nothing is looked up beyond the fixture repository.
  */
class PluginResolverSuite extends munit.FunSuite:

  private def consoleFor(stdout: String => Unit, stderr: String => Unit): com.histmersion.util.console.Console =
    new com.histmersion.util.console.Console {
      def write(value: Any): Unit = stdout(String.valueOf(value))
      def writeLine(value: Any): Unit = stdout(String.valueOf(value))
      def writeLine(): Unit = stdout("")
      def errorWrite(value: => String): Unit = stderr(value)
      def errorLine(value: => String): Unit = stderr(value)
      def errorLine(): Unit = stderr("")
      def errorFlush(): Unit = ()
      def readLine(): com.histmersion.util.console.Console.Line = throw new UnsupportedOperationException(
        "input not used"
      )
    }

  private val fixtures: Path =
    val text = String(getClass.getClassLoader.getResourceAsStream("plugin-fixtures-repo.txt").readAllBytes(), UTF_8)
    Path.of(text.linesIterator.collectFirst { case s"repo=$p" => p }.getOrElse(fail("no fixture repository")))

  private val Version = "0.1.0"
  private def published(artifact: String, ext: String): Path =
    fixtures.resolve(s"com/histmersion/$artifact/$Version/$artifact-$Version.$ext")

  final private case class Setup(kit: Path, plugins: Path, lib: Path, cache: Path):
    def args(extra: String*): List[String] =
      List("resolve", "--kit", kit.toString, "--lib", lib.toString, "--repo", fixtures.toString) ++ extra

  /** A kit shipping `shipped` (and nothing of the Scala library), a plugins directory with the plugin. */
  private def setup(shipped: List[String], withPom: Boolean = true): Setup =
    val root = Files.createTempDirectory("plugin-resolver-suite")
    val kit = Files.createDirectories(root.resolve("kit"))
    val plugins = Files.createDirectories(root.resolve("plugins"))
    Files.writeString(kit.resolve("modules"), shipped.mkString("", "\n", "\n"))
    Files.writeString(
      kit.resolve("excludes"),
      "org.scala-lang:scala3-library_3\norg.scala-lang:scala-library\nio.github.iltotore:iron_3\n"
    )
    Files.copy(published("histmersion-plugin-hello-deps_3", "jar"), plugins.resolve("hello-deps-0.1.0.jar"))
    if withPom then
      Files.copy(published("histmersion-plugin-hello-deps_3", "pom"), plugins.resolve("hello-deps-0.1.0.pom"))
    Setup(kit, plugins, plugins.resolve("lib"), root.resolve("cache"))

  private def run(s: Setup, extra: String*): (Int, List[String], List[String]) =
    val out = List.newBuilder[String]
    val err = List.newBuilder[String]
    val rc = DependencyResolver.parse(
      (s.args(extra*).tail :+ s.plugins.resolve("hello-deps-0.1.0.jar").toString).map(refactorValue =>
        Resolver.Argument(refactorValue)
      )
    ) match
      case Left(e)  => fail(e.sentences.mkString("; "))
      case Right(r) => DependencyResolver.resolve(r.copy(cache = Some(s.cache)))(using consoleFor(out += _, err += _))
    ((rc).value, out.result(), err.result())

  private def lines(p: Path): List[String] = Files.readAllLines(p, UTF_8).asScala.toList

  test("a plugin's own dependency is resolved from its POM into lib/, with the record files"):
    val s = setup(Nil)
    val (rc, out, err) = run(s)
    assertEquals(rc, 0, (out ++ err).mkString("\n"))
    assert(
      out.exists(
        _.startsWith("dependency: com.histmersion:histmersion-plugin-hello-lib_3:0.1.0 (for hello-deps-0.1.0.jar)")
      ),
      out.mkString("\n")
    )
    val name = "com.histmersion-histmersion-plugin-hello-lib_3-0.1.0.jar"
    assertEquals(lines(s.lib.resolve(LibDirectory.Classpath)), List(name))
    assertEquals(
      LibDirectory.sha256(s.lib.resolve(name)),
      LibDirectory.sha256(published("histmersion-plugin-hello-lib_3", "jar"))
    )
    assertEquals(
      lines(s.lib.resolve(LibDirectory.Inputs)).map(_.split("  ").last),
      List("hello-deps-0.1.0.jar", "hello-deps-0.1.0.pom")
    )
    assertEquals(lines(s.lib.resolve(LibDirectory.Repositories)), List(fixtures.toUri.toString))

  test("a module the server ships at the same or a newer version is not installed again"):
    val s = setup(List("com.histmersion:histmersion-plugin-hello-lib_3:0.2.0"))
    val (rc, out, err) = run(s)
    assertEquals(rc, 0, (out ++ err).mkString("\n"))
    assert(
      out.exists(_.startsWith("provided by Histmersion: 1 module(s) (com.histmersion:histmersion-plugin-hello-lib_3)")),
      out.mkString("\n")
    )
    assertEquals(lines(s.lib.resolve(LibDirectory.Classpath)), Nil)

  test("a plugin that needs a NEWER version than the server ships is refused, naming the library and both versions"):
    val s = setup(List("com.histmersion:histmersion-plugin-hello-lib_3:0.0.9"))
    val (rc, _, err) = run(s)
    assertEquals(rc, 2)
    assertEquals(
      err,
      List(
        "refused: hello-deps-0.1.0.jar needs com.histmersion:histmersion-plugin-hello-lib_3 0.1.0, but this Histmersion " +
          "ships 0.0.9 — build the plugin against 0.0.9 (the histmersion-plugin-platform POM), or install a Histmersion that ships 0.1.0"
      )
    )
    assert(!Files.exists(s.lib), "nothing written on a refusal")

  test("--check resolves and reports, and leaves lib/ as it is"):
    val s = setup(Nil)
    val (rc, out, _) = run(s, "--check")
    assertEquals(rc, 0)
    assert(out.exists(_.startsWith("dependency: com.histmersion:histmersion-plugin-hello-lib_3:0.1.0")))
    assert(!Files.exists(s.lib))

  test("a jar without a POM has no dependencies of its own; a jar no longer needed leaves lib/"):
    val s = setup(Nil)
    assertEquals(run(s)._1, 0)
    Files.delete(s.plugins.resolve("hello-deps-0.1.0.pom"))
    val (rc, out, err) = run(s)
    assertEquals(rc, 0, (out ++ err).mkString("\n"))
    assertEquals(lines(s.lib.resolve(LibDirectory.Classpath)), Nil)
    assertEquals(Files.list(s.lib).iterator().asScala.count(_.getFileName.toString.endsWith(".jar")), 0)
    assertEquals(lines(s.lib.resolve(LibDirectory.Inputs)).map(_.split("  ").last), List("hello-deps-0.1.0.jar"))

  test("the repositories of earlier runs are kept, and used when a run names none"):
    val s = setup(Nil)
    assertEquals(run(s)._1, 0)
    val again = DependencyResolver
      .parse(
        List(
          Resolver.Argument("--kit"),
          Resolver.Argument(s.kit.toString),
          Resolver.Argument("--lib"),
          Resolver.Argument(s.lib.toString),
          Resolver.Argument(s.plugins.resolve("hello-deps-0.1.0.jar").toString)
        )
      )
      .fold(e => fail(e.sentences.mkString("; ")), identity)
    assertEquals(
      ((DependencyResolver.resolve(again.copy(cache = Some(s.cache)))(using consoleFor(_ => (), _ => ()))).value: Int),
      0
    )
    assertEquals(lines(s.lib.resolve(LibDirectory.Repositories)), List(fixtures.toUri.toString))
    val other = RepositoryUrl
      .fromArgument(Resolver.Argument(s.kit.resolve("other-repo").toString))
      .fold(e => fail(e.sentences.mkString("; ")), identity)
    assertEquals(
      ((DependencyResolver
        .resolve(again.copy(cache = Some(s.cache), repositories = List(other)))(using consoleFor(_ => (), _ => ())))
        .value: Int),
      0
    )
    assertEquals(lines(s.lib.resolve(LibDirectory.Repositories)), List(other.value, fixtures.toUri.toString))

  test("a SNAPSHOT plugin version resolves as well"):
    val s = setup(Nil)
    val pom = s.plugins.resolve("hello-deps-0.1.0.pom")
    Files.writeString(
      pom,
      Files
        .readString(pom)
        .replace("<version>0.1.0</version>\n    <licenses>", "<version>0.2.0-SNAPSHOT</version>\n    <licenses>")
    )
    assert(
      Files.readString(pom).contains("0.2.0-SNAPSHOT"),
      "the fixture POM's layout changed: fix the replacement above"
    )
    val (rc, out, err) = run(s)
    assertEquals(rc, 0, (out ++ err).mkString("\n"))
    assertEquals(
      lines(s.lib.resolve(LibDirectory.Classpath)),
      List("com.histmersion-histmersion-plugin-hello-lib_3-0.1.0.jar")
    )

  test("usage: the kit and lib are required, an unknown option is named"):
    assert(DependencyResolver.parse(List(Resolver.Argument("x.jar"))).isLeft)
    assertEquals(
      ((DependencyResolver
        .parse(
          List(
            Resolver.Argument("--kit"),
            Resolver.Argument("k"),
            Resolver.Argument("--lib"),
            Resolver.Argument("l"),
            Resolver.Argument("--nope")
          )
        )
        .left
        .toOption)
        .map(_.toVector)),
      Some(
        Vector(
          Problem.Invalid.Unknown.here(
            Problem.TypeName("com.histmersion.pluginresolver.Resolver.Argument"),
            Problem.Raw("--nope"),
            Vector("--kit", "--lib", "--histmersion-repo", "--repo", "--check").map(r => Problem.Raw(s"$r"))
          )
        )
      )
    )
    assertEquals(
      ((DependencyResolver.run(List(Resolver.Argument("nothing")))(using consoleFor(_ => (), _ => ()))).value: Int),
      1
    )

  test("resolver exit boundary supplies errors and preserves failure status") {
    val out = List.newBuilder[String]
    val err = List.newBuilder[String]
    assertEquals(
      ((DependencyResolver
        .exitCode(List(Resolver.Argument("nothing")))(using consoleFor(s => { out += s; () }, s => { err += s; () })))
        .value: Int),
      1
    )
    assertEquals(out.result(), Nil)
    assert(err.result().head.startsWith("usage: pluginResolver"))
    val failures = List.newBuilder[String]
    var calls = 0
    val status = DependencyResolver.exitCode(List(Resolver.Argument("nothing")))(using
      consoleFor(
        _ => (),
        s => {
          calls += 1
          if calls == 1 then throw new IllegalArgumentException("sink failed")
          failures += s
          ()
        }
      )
    )
    assertEquals(((status).value: Int), 3)
    assertEquals(failures.result(), List("pluginResolver: IllegalArgumentException: sink failed"))
  }
