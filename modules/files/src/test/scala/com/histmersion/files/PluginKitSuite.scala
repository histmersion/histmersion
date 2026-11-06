package com.histmersion.files

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.given
import com.histmersion.api.Sha256
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.util.plugins.PluginKit.*
import com.histmersion.api.plugins.{PluginDescription, PluginFile, PluginJar, PluginSet}

class PluginKitSuite extends munit.FunSuite:

  private val a = List(PluginJar.File("a.jar") -> Sha256("11"), PluginJar.File("b.jar") -> Sha256("22"))

  test("digest: order-independent, content-sensitive, the empty set's is SHA-256 of nothing"):
    assertEquals(digest(a), digest(a.reverse))
    assertNotEquals(digest(a), digest(List(PluginJar.File("a.jar") -> Sha256("11"), PluginJar.File("b.jar") -> Sha256("23"))))
    assertNotEquals(digest(a), digest(List(PluginJar.File("a.jar") -> Sha256("11"), PluginJar.File("c.jar") -> Sha256("22"))))
    assertEquals(digest(Nil), PluginSet.EmptyDigest)
    assertEquals(sha256Hex(Array.emptyByteArray), PluginSet.EmptyDigest)

  test("pluginSet: the digest is over the jars' names and checksums; render/parse round-trips"):
    val jars = List(PluginJar(com.histmersion.api.plugins.PluginJar.File("b.jar"), com.histmersion.api.Sha256("22"), List(PluginDescription(com.histmersion.api.plugins.PluginDescription.Id("b"), com.histmersion.api.plugins.PluginDescription.Version("1.0"), com.histmersion.api.plugins.PluginDescription.SpiVersion(1)))), PluginJar(com.histmersion.api.plugins.PluginJar.File("a.jar"), com.histmersion.api.Sha256("11")))
    val s = pluginSet(jars, com.histmersion.api.plugins.PluginSet.SpiVersion(1), com.histmersion.api.plugins.PluginSet.Mode("native"), Some(com.histmersion.api.plugins.PluginSet.Kit("0.1.0 (abc)")), Some(com.histmersion.api.plugins.PluginSet.LinkedAt("2026-10-18T12:00:00Z")))
    assertEquals(s.digest, digest(a))
    assertEquals(s.jars.map(_.file), List("a.jar", "b.jar"))
    assertEquals(parseSet(renderSet(s)), Right(s))
    assert(!renderSet(pluginSet(Nil, com.histmersion.api.plugins.PluginSet.SpiVersion(1), com.histmersion.api.plugins.PluginSet.Mode("jvm"), None, None)).contains("null"), "absent options are left out")
    assert(parseSet("{").isLeft)

  test("PluginSet.describe: first-party only, or the jars"):
    assert(PluginSet.describe(None).value.startsWith("first-party plugins only"))
    val s = pluginSet(List(PluginJar(com.histmersion.api.plugins.PluginJar.File("hello-0.1.0.jar"), com.histmersion.api.Sha256("11"))), com.histmersion.api.plugins.PluginSet.SpiVersion(1), com.histmersion.api.plugins.PluginSet.Mode("native"), None, None)
    assert(PluginSet.describe(Some(s)).value.contains("1 jar (native): hello-0.1.0.jar"), PluginSet.describe(Some(s)))

  private def set(entries: List[(PluginJar.File, Sha256)]) =
    pluginSet(entries.map((f, h) => PluginJar(f, h)), com.histmersion.api.plugins.PluginSet.SpiVersion(1), com.histmersion.api.plugins.PluginSet.Mode("native"), None, None)

  test("status: up to date, relink needed (added/removed/changed), restart needed, unknown"):
    val linked = set(a)
    assertEquals(status(a, Some(linked), Some(linked), serverAnswered = true).verdict, Verdict.UpToDate)
    val changed = status(List(PluginJar.File("a.jar") -> Sha256("11"), PluginJar.File("b.jar") -> Sha256("99"), PluginJar.File("c.jar") -> Sha256("33")), Some(linked), Some(linked), true)
    assertEquals(changed.verdict, Verdict.RelinkNeeded)
    assertEquals((changed.added, changed.removed, changed.changed), (List("c.jar"), Nil, List("b.jar")))
    val removed = status(a.take(1), Some(linked), None, false)
    assertEquals((removed.verdict, removed.removed), (Verdict.RelinkNeeded, List("b.jar")))
    // relinked with --no-restart: the running server still reports the old (here: the empty) set
    assertEquals(status(a, Some(linked), None, serverAnswered = true).verdict, Verdict.RestartNeeded)
    // the server did not answer: nothing to say about it
    assertEquals(status(a, Some(linked), None, serverAnswered = false).verdict, Verdict.UpToDate)
    assertEquals(status(a, None, None, false).verdict, Verdict.Unknown)
    // a stock install: empty directory, empty record, a server reporting no set
    assertEquals(status(Nil, Some(set(Nil)), None, true).verdict, Verdict.UpToDate)

  test(
    "status: a changed POM or lib/ jar beside the same jars needs a rebuild; a record without them compares the jars"
  ):
    val deps = List(PluginFile.File("a.pom") -> Sha256("p1"), PluginFile.File("lib/x-1.0.jar") -> Sha256("x1"))
    val linked = pluginSet(a.map((f, h) => PluginJar(f, h)), com.histmersion.api.plugins.PluginSet.SpiVersion(1), com.histmersion.api.plugins.PluginSet.Mode("native"), None, None, deps)
    assertEquals(status(a, Some(linked), Some(linked), true, deps).verdict, Verdict.UpToDate)
    val pom = status(a, Some(linked), Some(linked), true, List(PluginFile.File("a.pom") -> Sha256("p2"), PluginFile.File("lib/x-1.0.jar") -> Sha256("x1")))
    assertEquals((pom.verdict, pom.changed), (Verdict.RelinkNeeded, List("a.pom")))
    val lib = status(a, Some(linked), Some(linked), true, List(PluginFile.File("a.pom") -> Sha256("p1"), PluginFile.File("lib/x-1.1.jar") -> Sha256("x2")))
    assertEquals(
      (lib.verdict, lib.added, lib.removed),
      (Verdict.RelinkNeeded, List("lib/x-1.1.jar"), List("lib/x-1.0.jar"))
    )
    assert(
      statusLines(lib, true).mkString("\n").contains("REBUILD NEEDED: the plugins directory (its jars, POMs or lib/)")
    )
    // a record written before the POMs and lib/ were recorded: the jars alone decide, as they did
    val old = linked.modify(_.dependencies).setTo(None)
    assertEquals(status(a, Some(old), Some(old), true, List(PluginFile.File("a.pom") -> Sha256("p2"))).verdict, Verdict.UpToDate)

  test("statusLines say what to do"):
    val st = status(a, Some(set(Nil)), None, true)
    val text = statusLines(st, true).mkString("\n")
    assert(text.contains("REBUILD NEEDED"), text)
    assert(text.contains("added since the link: a.jar"), text)

  test("coordinates: g:a:v, g::a:v (Scala 3), refused shapes; the jar URL"):
    assertEquals(
      parseCoordinates("org.example:portal-plugin:1.2.0"),
      Right(Coordinates(com.histmersion.util.plugins.PluginKit.Coordinates.Group("org.example"), com.histmersion.util.plugins.PluginKit.Coordinates.Artifact("portal-plugin"), com.histmersion.util.plugins.PluginKit.Coordinates.Version("1.2.0")))
    )
    assertEquals(parseCoordinates("org.example::portal:1.2.0"), Right(Coordinates(com.histmersion.util.plugins.PluginKit.Coordinates.Group("org.example"), com.histmersion.util.plugins.PluginKit.Coordinates.Artifact("portal_3"), com.histmersion.util.plugins.PluginKit.Coordinates.Version("1.2.0"))))
    assert(parseCoordinates("org.example:portal").isLeft)
    assert(parseCoordinates("a:b:c:d").isLeft)
    assert(parseCoordinates("a b:c:d").isLeft)
    assertEquals(
      Coordinates(com.histmersion.util.plugins.PluginKit.Coordinates.Group("org.example"), com.histmersion.util.plugins.PluginKit.Coordinates.Artifact("portal_3"), com.histmersion.util.plugins.PluginKit.Coordinates.Version("1.2.0")).jarUrl("https://repo.example/maven2/"),
      "https://repo.example/maven2/org/example/portal_3/1.2.0/portal_3-1.2.0.jar"
    )

  test("origin: URL, path, coordinates"):
    val none = (_: String) => false
    assertEquals(origin("https://x.example/p.jar", none), Right(Origin.Url(com.histmersion.util.plugins.PluginKit.Origin.Url.Value("https://x.example/p.jar"))))
    assertEquals(origin("plugins/p.jar", none), Right(Origin.File(com.histmersion.util.plugins.PluginKit.Origin.File.Path("plugins/p.jar"))))
    assertEquals(origin("p.jar", none), Right(Origin.File(com.histmersion.util.plugins.PluginKit.Origin.File.Path("p.jar"))))
    assertEquals(origin("weird-name", _ == "weird-name"), Right(Origin.File(com.histmersion.util.plugins.PluginKit.Origin.File.Path("weird-name"))))
    assertEquals(origin("org.x:p:1", none), Right(Origin.Maven(Coordinates(com.histmersion.util.plugins.PluginKit.Coordinates.Group("org.x"), com.histmersion.util.plugins.PluginKit.Coordinates.Artifact("p"), com.histmersion.util.plugins.PluginKit.Coordinates.Version("1")))))
    assert(origin("nonsense", none).isLeft)

  test("jarName: <id>-<version>.jar for one plugin, else the name it came with, cleaned"):
    assertEquals(jarName(List(PluginDescription(com.histmersion.api.plugins.PluginDescription.Id("hello"), com.histmersion.api.plugins.PluginDescription.Version("0.1.0"), com.histmersion.api.plugins.PluginDescription.SpiVersion(1))), "x.jar"), "hello-0.1.0.jar")
    assertEquals(jarName(Nil, "../evil name.jar"), ".._evil_name.jar")
    assertEquals(jarName(List(PluginDescription(com.histmersion.api.plugins.PluginDescription.Id("a"), com.histmersion.api.plugins.PluginDescription.Version("1"), com.histmersion.api.plugins.PluginDescription.SpiVersion(1)), PluginDescription(com.histmersion.api.plugins.PluginDescription.Id("b"), com.histmersion.api.plugins.PluginDescription.Version("1"), com.histmersion.api.plugins.PluginDescription.SpiVersion(1))), "pair"), "pair.jar")

  test("isLoopback: the plugin commands act on this machine only"):
    List("http://localhost:8000", "http://127.0.0.1:18000/", "http://[::1]:8000", "http://LOCALHOST").foreach(u =>
      assert(isLoopback(u), u)
    )
    List("http://192.168.1.4:8000", "https://histmersion.example.org", "not a url", "").foreach(u =>
      assert(!isLoopback(u), u)
    )

  test("index: put replaces by file, drop, ids of a jar, render/parse"):
    val h = Installed(
      com.histmersion.util.plugins.PluginKit.Installed.File("hello-0.1.0.jar"),
      com.histmersion.util.plugins.PluginKit.Installed.Sha256.applyUnsafe("11" * 32),
      com.histmersion.util.plugins.PluginKit.Installed.Source("/tmp/h.jar"),
      com.histmersion.util.plugins.PluginKit.Installed.AddedAt("2026-10-18T00:00:00Z"),
      List(PluginDescription(com.histmersion.api.plugins.PluginDescription.Id("hello"), com.histmersion.api.plugins.PluginDescription.Version("0.1.0"), com.histmersion.api.plugins.PluginDescription.SpiVersion(1)))
    )
    val i = Index().put(h).put(h.modify(_.sha256).setTo(com.histmersion.util.plugins.PluginKit.Installed.Sha256.applyUnsafe("12" * 32)))
    assertEquals(i.jars.map(_.sha256), List(com.histmersion.util.plugins.PluginKit.Installed.Sha256.applyUnsafe("12" * 32)))
    assertEquals(parseIndex(renderIndex(i)), Right(i))
    assertEquals(idsOf(com.histmersion.util.plugins.PluginKit.Installed.File("hello-0.1.0.jar"), i), List("hello"))
    assertEquals(idsOf(com.histmersion.util.plugins.PluginKit.Installed.File("dropped-plugin-2.3.1.jar"), i), List("dropped-plugin"))
    assertEquals(idsOf(com.histmersion.util.plugins.PluginKit.Installed.File("noversion.jar"), i), List("noversion"))
    assertEquals(i.drop(com.histmersion.util.plugins.PluginKit.Installed.File("hello-0.1.0.jar")), Index())

  test("checkSigner: the first signed install pins the key; another key is refused"):
    val signed = Index().put(Installed(com.histmersion.util.plugins.PluginKit.Installed.File("p-1.jar"), com.histmersion.util.plugins.PluginKit.Installed.Sha256.applyUnsafe("1" * 64), com.histmersion.util.plugins.PluginKit.Installed.Source("s"), com.histmersion.util.plugins.PluginKit.Installed.AddedAt("t"), List(PluginDescription(com.histmersion.api.plugins.PluginDescription.Id("p"), com.histmersion.api.plugins.PluginDescription.Version("1"), com.histmersion.api.plugins.PluginDescription.SpiVersion(1))), Some(com.histmersion.util.plugins.PluginKit.Installed.Signer("AAAA"))))
    assertEquals(checkSigner(List(com.histmersion.api.plugins.PluginDescription.Id("p")), com.histmersion.util.plugins.PluginKit.Installed.Signer("AAAA"), signed), Right(()))
    assert(checkSigner(List(com.histmersion.api.plugins.PluginDescription.Id("p")), com.histmersion.util.plugins.PluginKit.Installed.Signer("BBBB"), signed).left.exists(_.value.contains("signed by AAAA")))
    assertEquals(checkSigner(List(com.histmersion.api.plugins.PluginDescription.Id("q")), com.histmersion.util.plugins.PluginKit.Installed.Signer("BBBB"), signed), Right(()))

  test("refusals: no plugin, a foreign SPI, a broken provider, an id taken"):
    val ok = PluginJar(com.histmersion.api.plugins.PluginJar.File("p.jar"), com.histmersion.api.Sha256("1"), List(PluginDescription(com.histmersion.api.plugins.PluginDescription.Id("p"), com.histmersion.api.plugins.PluginDescription.Version("1.0"), com.histmersion.api.plugins.PluginDescription.SpiVersion(1))))
    assertEquals(refusals(ok, com.histmersion.api.plugins.PluginSet.SpiVersion(1), Map.empty), Nil)
    assert(refusals(PluginJar(com.histmersion.api.plugins.PluginJar.File("e.jar"), com.histmersion.api.Sha256("1")), com.histmersion.api.plugins.PluginSet.SpiVersion(1), Map.empty).head.value.contains("holds no plugin"))
    assert(refusals(ok, com.histmersion.api.plugins.PluginSet.SpiVersion(2), Map.empty).head.value.contains("SPI 1; this Histmersion speaks SPI 2"))
    assert(
      refusals(PluginJar(com.histmersion.api.plugins.PluginJar.File("b.jar"), com.histmersion.api.Sha256("1"), List(PluginDescription(com.histmersion.api.plugins.PluginDescription.Id(""), com.histmersion.api.plugins.PluginDescription.Version(""), com.histmersion.api.plugins.PluginDescription.SpiVersion(0), error = Some(com.histmersion.api.plugins.PluginDescription.Error("boom"))))), com.histmersion.api.plugins.PluginSet.SpiVersion(0), Map.empty)
        .exists(_.value.contains("could not be loaded: boom"))
    )
    assert(refusals(ok, com.histmersion.api.plugins.PluginSet.SpiVersion(1), Map(PluginDescription.Id("p") -> "other.jar")).head.value.contains("already provided by other.jar"))

  test("refusals: a provider that could not be loaded is refused for its own reason alone, never for an SPI 0"):
    val foreign = "it was built against plugin SPI 1; this Histmersion speaks SPI 2 — rebuild the plugin against SPI 2"
    val broken = PluginJar(com.histmersion.api.plugins.PluginJar.File("b.jar"), com.histmersion.api.Sha256("1"), List(PluginDescription(com.histmersion.api.plugins.PluginDescription.Id(""), com.histmersion.api.plugins.PluginDescription.Version(""), com.histmersion.api.plugins.PluginDescription.SpiVersion(0), error = Some(com.histmersion.api.plugins.PluginDescription.Error(foreign)))))
    assertEquals(refusals(broken, com.histmersion.api.plugins.PluginSet.SpiVersion(2), Map.empty), List(s"a provider in b.jar could not be loaded: $foreign"))
    val missing = PluginJar(com.histmersion.api.plugins.PluginJar.File("m.jar"), com.histmersion.api.Sha256("1"), List(PluginDescription(com.histmersion.api.plugins.PluginDescription.Id(""), com.histmersion.api.plugins.PluginDescription.Version(""), com.histmersion.api.plugins.PluginDescription.SpiVersion(0), error = Some(com.histmersion.api.plugins.PluginDescription.Error("class x.Y is missing")))))
    assertEquals(
      refusals(missing, com.histmersion.api.plugins.PluginSet.SpiVersion(2), Map(PluginDescription.Id("") -> "other.jar")),
      List("a provider in m.jar could not be loaded: class x.Y is missing")
    )

  test("kit manifest: SHA256SUMS round trip; verify names missing, changed and extra files"):
    val files = List(KitFile(com.histmersion.util.plugins.PluginKit.KitFile.File("lib/b.jar"), KitFile.Sha256.applyUnsafe("b" * 64)), KitFile(com.histmersion.util.plugins.PluginKit.KitFile.File("classpath"), KitFile.Sha256.applyUnsafe("c" * 64)))
    val sums = sha256sums(files)
    assertEquals(sums.linesIterator.toList.head, s"${"c" * 64}  classpath")
    assertEquals(parseSha256sums(sums).map(_.sortBy(_.file.value)), Right(files.sortBy(_.file.value)))
    assert(parseSha256sums("zz  x\n").isLeft)
    assertEquals(verifyKit(files, Map(KitFile.File("lib/b.jar") -> KitFile.Sha256.applyUnsafe("b" * 64), KitFile.File("classpath") -> KitFile.Sha256.applyUnsafe("c" * 64))), Nil)
    assertEquals(
      verifyKit(files, Map(KitFile.File("lib/b.jar") -> KitFile.Sha256.applyUnsafe("d" * 64), KitFile.File("lib/x.jar") -> KitFile.Sha256.applyUnsafe("e" * 64))),
      List("missing: classpath", "changed: lib/b.jar", "not in the manifest: lib/x.jar")
    )
    val k =
      KitManifest(KitFormat, com.histmersion.util.plugins.PluginKit.KitManifest.Version("0.1.0"), Some(com.histmersion.util.plugins.PluginKit.KitManifest.Commit("abc")), com.histmersion.util.plugins.PluginKit.KitManifest.Graalvm("25.0.4.1.1"), com.histmersion.api.plugins.PluginSet.SpiVersion(1), com.histmersion.util.plugins.PluginKit.KitManifest.MainClass("com.histmersion.server.histmersionServer"), files)
    assertEquals(parseKit(renderKit(k)), Right(k))
    assert(parseKit(renderKit(k.modify(_.format).setTo(KitManifest.Format(KitFormat.value + 1)))).isLeft, "a newer kit format is refused")
    assertEquals(k.label, "0.1.0 (abc)")
