package com.histmersion.server.plugins

import com.histmersion.util.plugins.PluginSetResource

import com.histmersion.api.{PluginJar, PluginSet}
import com.histmersion.files.*
import com.histmersion.files.PluginKit
import com.histmersion.plugin.Spi

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.jar.{JarEntry, JarFile, JarOutputStream}
import scala.jdk.CollectionConverters.*

/** The relink kit's JVM tool against a REAL third-party plugin jar: modules/plugin-fixtures/hello, built by sbt and
  * never on the server's class path.
  */
class PluginToolSuite extends munit.FunSuite:

  private lazy val hello: Path =
    val in = getClass.getClassLoader.getResourceAsStream("plugin-fixtures.txt")
    val line = String(in.readAllBytes(), UTF_8).linesIterator.find(_.startsWith("hello=")).get
    Path.of(line.stripPrefix("hello="))

  /** The hello-deps fixture (its POM lists hello-lib) and hello-lib's jar, from the fixture repository. */
  private lazy val (helloDeps: Path, helloLib: Path) =
    val in = getClass.getClassLoader.getResourceAsStream("plugin-fixtures.txt")
    val repo =
      Path.of(String(in.readAllBytes(), UTF_8).linesIterator.find(_.startsWith("repo=")).get.stripPrefix("repo="))
    def jar(a: String) = repo.resolve(s"com/histmersion/$a/0.1.0/$a-0.1.0.jar")
    (jar("histmersion-plugin-hello-deps_3"), jar("histmersion-plugin-hello-lib_3"))

  private val tmp = FunFixture[Path](
    _ => Files.createTempDirectory("plugin-tool"),
    d =>
      Files.walk(d).sorted(java.util.Comparator.reverseOrder()).forEach { p =>
        val _ = Files.deleteIfExists(p)
      }
  )

  /** A copy of `src` with `services` as its ServiceLoader file and `extra` entries added. */
  private def variant(
      src: Path,
      out: Path,
      services: Option[String],
      extra: Map[String, Array[Byte]] = Map.empty,
      keep: String => Boolean = _ => true
  ): Path =
    val jf = JarFile(src.toFile)
    val jos = JarOutputStream(Files.newOutputStream(out))
    try
      jf.entries().asScala.filter(e => keep(e.getName)).foreach { e =>
        if e.getName == "META-INF/services/com.histmersion.plugin.Plugin" then
          services.foreach { s =>
            jos.putNextEntry(JarEntry(e.getName)); jos.write(s.getBytes(UTF_8)); jos.closeEntry()
          }
        else
          jos.putNextEntry(JarEntry(e.getName))
          jos.write(jf.getInputStream(e).readAllBytes())
          jos.closeEntry()
      }
      extra.foreach { (n, b) => jos.putNextEntry(JarEntry(n)); jos.write(b); jos.closeEntry() }
    finally { jos.close(); jf.close() }
    out

  private def loader = getClass.getClassLoader

  test("describe: the hello plugin's id, version, SPI, what it registers and its access"):
    val j = RelinkPlugins.describe(hello, loader)
    assertEquals(j.file, hello.getFileName.toString)
    assertEquals(j.sha256, RelinkPlugins.sha256(hello))
    assertEquals(j.plugins.map(p => (p.id, p.version, p.spiVersion)), List(("hello", "0.1.0", Spi.Version)))
    assertEquals(j.plugins.head.provides, List("source-fetcher:hello"))
    assertEquals(j.plugins.head.access, List("open"))
    assertEquals(j.plugins.head.error, None)

  tmp.test("describe: a foreign SPI is reported, a jar without a provider holds nothing"): d =>
    val foreign = variant(hello, d.resolve("foreign.jar"), Some("com.histmersion.fixtures.hello.ForeignSpiPlugin\n"))
    val f = RelinkPlugins.describe(foreign, loader)
    assertEquals(f.plugins.map(p => (p.id, p.spiVersion)), List(("foreign-spi", Spi.Version + 98)))
    assert(f.plugins.head.error.exists(_.contains("SPI")), f.plugins.head.error)
    val none = variant(hello, d.resolve("none.jar"), None)
    assertEquals(RelinkPlugins.describe(none, loader).plugins, Nil)
    val broken = variant(hello, d.resolve("broken.jar"), Some("com.histmersion.fixtures.hello.NoSuchPlugin\n"))
    val b = RelinkPlugins.describe(broken, loader)
    assert(b.plugins.headOption.flatMap(_.error).exists(_.contains("NoSuchPlugin")), b)

  test("duplicates: a class the kit or an earlier plugin already has, named with its owner"):
    val kit = Set("a/A.class", "a/B.class")
    assertEquals(RelinkPlugins.duplicates(kit, List("p.jar" -> Set("p/P.class"))), Nil)
    val dups =
      RelinkPlugins.duplicates(kit, List("p.jar" -> Set("a/A.class", "p/P.class"), "q.jar" -> Set("p/P.class")))
    assertEquals(dups.size, 2)
    assert(dups.head.startsWith("p.jar duplicates") && dups.head.contains("1 with the kit (a.A)"), dups.head)
    assert(dups(1).startsWith("q.jar duplicates") && dups(1).contains("1 with p.jar (p.P)"), dups(1))

  private def kitWith(d: Path, jars: Map[String, Map[String, Array[Byte]]]): Path =
    val kit = Files.createDirectories(d.resolve("kit").resolve("lib")).getParent
    jars.foreach { (name, entries) =>
      val jos = JarOutputStream(Files.newOutputStream(kit.resolve("lib").resolve(name)))
      try entries.foreach { (n, b) => jos.putNextEntry(JarEntry(n)); jos.write(b); jos.closeEntry() }
      finally jos.close()
    }
    Files.writeString(kit.resolve("classpath"), jars.keys.mkString("", "\n", "\n"))
    kit

  tmp.test("check: the hello plugin passes against a kit that does not have its classes"): d =>
    val kit = kitWith(d, Map("other.jar" -> Map("x/X.class" -> Array[Byte](1))))
    val (lines, problems) = RelinkPlugins.check(kit, List(hello), Nil, loader)
    assertEquals(problems, Nil)
    assertEquals(
      lines,
      List(
        s"plugin: hello 0.1.0 (SPI ${Spi.Version}) in ${hello.getFileName} · access open · provides source-fetcher:hello"
      )
    )

  tmp.test("check: refuses a class twice, a foreign SPI, the same id twice, an id the binary has"): d =>
    val cls = "com/histmersion/fixtures/hello/HelloFetcher.class"
    val kit = kitWith(d, Map("shaded-elsewhere.jar" -> Map(cls -> Array[Byte](1))))
    val (_, dup) = RelinkPlugins.check(kit, List(hello), Nil, loader)
    assert(
      dup.exists(p =>
        p.contains("duplicates classes") && p.contains("the kit") &&
          p.contains("com.histmersion.fixtures.hello.HelloFetcher")
      ),
      dup
    )
    val clean = kitWith(Files.createDirectories(d.resolve("k2")), Map.empty)
    val foreign = variant(hello, d.resolve("foreign.jar"), Some("com.histmersion.fixtures.hello.ForeignSpiPlugin\n"))
    val (_, spi) = RelinkPlugins.check(clean, List(foreign), Nil, loader)
    assert(spi.exists(_.contains(s"built for plugin SPI ${Spi.Version + 98}")), spi)
    val twin = variant(hello, d.resolve("hello-copy.jar"), Some("com.histmersion.fixtures.hello.HelloPlugin\n"))
    val (_, twice) = RelinkPlugins.check(clean, List(hello, twin), Nil, loader)
    assert(twice.exists(_.contains(s"plugin id 'hello' is already provided by ${hello.getFileName}")), twice)
    assert(twice.exists(_.startsWith("hello-copy.jar duplicates classes")), twice)
    assertEquals(RelinkPlugins.hostIds(loader).get("archive-org"), Some("this Histmersion (a first-party plugin)"))
    assert(RelinkPlugins.hostIds(loader).contains("keepassxc"), RelinkPlugins.hostIds(loader).keys)
    // the open-access indexes (D4), and the former id their settings are still read under
    assert(Set("oa-unpaywall", "oa-core", "oa-openalex", "oa-landing").subsetOf(RelinkPlugins.hostIds(loader).keySet))
    assert(
      RelinkPlugins.hostIds(loader).get("oa-index").exists(_.contains("former first-party plugin")),
      RelinkPlugins.hostIds(loader)
    )

  tmp.test("describe: a plugin is loaded with its dependencies from lib/; without them, the missing class is named"):
    d =>
      val lib = Files.createDirectories(d.resolve("lib"))
      Files.copy(helloLib, lib.resolve("com.histmersion-hello-lib.jar"))
      Files.writeString(lib.resolve("classpath"), "com.histmersion-hello-lib.jar\n")
      assertEquals(RelinkPlugins.dependencyJars(lib), List(lib.resolve("com.histmersion-hello-lib.jar")))
      val withDeps = RelinkPlugins.describe(
        helloDeps,
        URLClassLoader(RelinkPlugins.dependencyJars(lib).map(_.toUri.toURL).toArray, loader)
      )
      assertEquals(
        withDeps.plugins.map(p => (p.id, p.provides, p.error)),
        List(("hello-deps", List("source-fetcher:hello-deps"), None))
      )
      val without = RelinkPlugins.describe(helloDeps, loader).plugins.head
      assert(
        without.error.exists(e =>
          e.contains("class com.histmersion.fixtures.hellolib.Shout") && e.contains("is missing") &&
            e.contains("histmersion plugin add")
        ),
        without.error
      )
      assertEquals(RelinkPlugins.dependencyJars(d.resolve("no-lib")), Nil)

  tmp.test("linkedFiles: the POM beside each jar and the lib/ jars, with their checksums"): d =>
    val jar = d.resolve("x-1.0.jar"); Files.write(jar, Array[Byte](1))
    val pom = d.resolve("x-1.0.pom"); Files.writeString(pom, "<project/>")
    val bare = d.resolve("y-1.0.jar"); Files.write(bare, Array[Byte](2))
    assertEquals(
      RelinkPlugins.linkedFiles(List(jar, bare), List(helloLib)),
      List("x-1.0.pom" -> RelinkPlugins.sha256(pom), s"lib/${helloLib.getFileName}" -> RelinkPlugins.sha256(helloLib))
    )

  tmp.test("check: a dependency jar that holds a class the kit already has is refused"): d =>
    val kit =
      kitWith(d, Map("lib-elsewhere.jar" -> Map("com/histmersion/fixtures/hellolib/Shout.class" -> Array[Byte](1))))
    val (_, problems) = RelinkPlugins.check(kit, List(helloDeps), List(helloLib), loader)
    assert(
      problems.exists(p =>
        p.startsWith(helloLib.getFileName.toString + " duplicates classes") &&
          p.contains("com.histmersion.fixtures.hellolib.Shout") && p.contains("depend on the library")
      ),
      problems
    )

  tmp.test("plugin-set jar: written, found on a class path, the digest over the jars' names and checksums"): d =>
    val set =
      PluginKit.pluginSet(List(RelinkPlugins.describe(hello, loader)), Spi.Version, "native", Some("0.1.0 (abc)"), None)
    val jar = d.resolve("plugin-set.jar")
    RelinkPlugins.writePluginSet(jar, set)
    val found = PluginSetResource.load(URLClassLoader(Array(jar.toUri.toURL), null))
    assertEquals(found, Right(Some(set)))
    assertEquals(set.digest, PluginKit.digest(List(hello.getFileName.toString -> RelinkPlugins.sha256(hello))))
    // its own native-image metadata registers the resource, as a plugin jar's does
    val jf = JarFile(jar.toFile)
    try
      val meta = String(
        jf.getInputStream(jf.getEntry("META-INF/native-image/com.histmersion/plugin-set/reachability-metadata.json"))
          .readAllBytes(),
        UTF_8
      )
      assert(meta.contains(PluginSet.Resource), meta)
    finally jf.close()
    assertEquals(PluginSetResource.load(URLClassLoader(Array.empty, null)), Right(None))

  tmp.test("kit manifest: every file but the manifests, with its SHA-256, and the main class"): d =>
    val kit = kitWith(d, Map("a.jar" -> Map("a/A.class" -> Array[Byte](1))))
    Files.writeString(kit.resolve("main-class"), "com.histmersion.server.histmersionServer\n")
    Files.writeString(kit.resolve("kit.json"), "{}")
    val m = RelinkPlugins.kitManifest(kit, "0.1.0", Some("abc"), "25.0.4.1.1")
    assertEquals(m.files.map(_.file), List("classpath", "lib/a.jar", "main-class"))
    assertEquals(
      m.files.find(_.file == "lib/a.jar").map(_.sha256),
      Some(RelinkPlugins.sha256(kit.resolve("lib/a.jar")))
    )
    assertEquals(
      (m.mainClass, m.graalvm, m.spiVersion),
      ("com.histmersion.server.histmersionServer", "25.0.4.1.1", Spi.Version)
    )
    val actual = m.files.map(f => f.file -> RelinkPlugins.sha256(kit.resolve(f.file))).toMap
    assertEquals(PluginKit.verifyKit(m.files, actual), Nil)
