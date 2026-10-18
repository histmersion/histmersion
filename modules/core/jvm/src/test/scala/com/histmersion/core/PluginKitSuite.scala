package com.histmersion.core

import com.histmersion.core.PluginKit.*

class PluginKitSuite extends munit.FunSuite:

  private val a = List("a.jar" -> "11", "b.jar" -> "22")

  test("digest: order-independent, content-sensitive, the empty set's is SHA-256 of nothing"):
    assertEquals(digest(a), digest(a.reverse))
    assertNotEquals(digest(a), digest(List("a.jar" -> "11", "b.jar" -> "23")))
    assertNotEquals(digest(a), digest(List("a.jar" -> "11", "c.jar" -> "22")))
    assertEquals(digest(Nil), PluginSet.EmptyDigest)
    assertEquals(sha256Hex(Array.emptyByteArray), PluginSet.EmptyDigest)

  test("pluginSet: the digest is over the jars' names and checksums; render/parse round-trips"):
    val jars = List(PluginJar("b.jar", "22", List(PluginDescription("b", "1.0", 1))), PluginJar("a.jar", "11"))
    val s = pluginSet(jars, 1, "native", Some("0.1.0 (abc)"), Some("2026-10-18T12:00:00Z"))
    assertEquals(s.digest, digest(a))
    assertEquals(s.jars.map(_.file), List("a.jar", "b.jar"))
    assertEquals(parseSet(renderSet(s)), Right(s))
    assert(!renderSet(pluginSet(Nil, 1, "jvm", None, None)).contains("null"), "absent options are left out")
    assert(parseSet("{").isLeft)

  test("PluginSet.describe: first-party only, or the jars"):
    assert(PluginSet.describe(None).startsWith("first-party plugins only"))
    val s = pluginSet(List(PluginJar("hello-0.1.0.jar", "11")), 1, "native", None, None)
    assert(PluginSet.describe(Some(s)).contains("1 jar (native): hello-0.1.0.jar"), PluginSet.describe(Some(s)))

  private def set(entries: List[(String, String)]) = pluginSet(entries.map((f, h) => PluginJar(f, h)), 1, "native", None, None)

  test("status: up to date, relink needed (added/removed/changed), restart needed, unknown"):
    val linked = set(a)
    assertEquals(status(a, Some(linked), Some(linked), serverAnswered = true).verdict, Verdict.UpToDate)
    val changed = status(List("a.jar" -> "11", "b.jar" -> "99", "c.jar" -> "33"), Some(linked), Some(linked), true)
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

  test("statusLines say what to do"):
    val st = status(a, Some(set(Nil)), None, true)
    val text = statusLines(st, true).mkString("\n")
    assert(text.contains("REBUILD NEEDED"), text)
    assert(text.contains("added since the link: a.jar"), text)

  test("coordinates: g:a:v, g::a:v (Scala 3), refused shapes; the jar URL"):
    assertEquals(parseCoordinates("org.example:portal-plugin:1.2.0"), Right(Coordinates("org.example", "portal-plugin", "1.2.0")))
    assertEquals(parseCoordinates("org.example::portal:1.2.0"), Right(Coordinates("org.example", "portal_3", "1.2.0")))
    assert(parseCoordinates("org.example:portal").isLeft)
    assert(parseCoordinates("a:b:c:d").isLeft)
    assert(parseCoordinates("a b:c:d").isLeft)
    assertEquals(Coordinates("org.example", "portal_3", "1.2.0").jarUrl("https://repo.example/maven2/"),
      "https://repo.example/maven2/org/example/portal_3/1.2.0/portal_3-1.2.0.jar")

  test("origin: URL, path, coordinates"):
    val none = (_: String) => false
    assertEquals(origin("https://x.example/p.jar", none), Right(Origin.Url("https://x.example/p.jar")))
    assertEquals(origin("plugins/p.jar", none), Right(Origin.File("plugins/p.jar")))
    assertEquals(origin("p.jar", none), Right(Origin.File("p.jar")))
    assertEquals(origin("weird-name", _ == "weird-name"), Right(Origin.File("weird-name")))
    assertEquals(origin("org.x:p:1", none), Right(Origin.Maven(Coordinates("org.x", "p", "1"))))
    assert(origin("nonsense", none).isLeft)

  test("jarName: <id>-<version>.jar for one plugin, else the name it came with, cleaned"):
    assertEquals(jarName(List(PluginDescription("hello", "0.1.0", 1)), "x.jar"), "hello-0.1.0.jar")
    assertEquals(jarName(Nil, "../evil name.jar"), ".._evil_name.jar")
    assertEquals(jarName(List(PluginDescription("a", "1", 1), PluginDescription("b", "1", 1)), "pair"), "pair.jar")

  test("isLoopback: the plugin commands act on this machine only"):
    List("http://localhost:8000", "http://127.0.0.1:18000/", "http://[::1]:8000", "http://LOCALHOST").foreach(u =>
      assert(isLoopback(u), u))
    List("http://192.168.1.4:8000", "https://histmersion.example.org", "not a url", "").foreach(u =>
      assert(!isLoopback(u), u))

  test("index: put replaces by file, drop, ids of a jar, render/parse"):
    val h = Installed("hello-0.1.0.jar", "11", "/tmp/h.jar", "2026-10-18T00:00:00Z", List(PluginDescription("hello", "0.1.0", 1)))
    val i = Index().put(h).put(h.copy(sha256 = "12"))
    assertEquals(i.jars.map(_.sha256), List("12"))
    assertEquals(parseIndex(renderIndex(i)), Right(i))
    assertEquals(idsOf("hello-0.1.0.jar", i), List("hello"))
    assertEquals(idsOf("dropped-plugin-2.3.1.jar", i), List("dropped-plugin"))
    assertEquals(idsOf("noversion.jar", i), List("noversion"))
    assertEquals(i.drop("hello-0.1.0.jar"), Index())

  test("checkSigner: the first signed install pins the key; another key is refused"):
    val signed = Index().put(Installed("p-1.jar", "1", "s", "t", List(PluginDescription("p", "1", 1)), Some("AAAA")))
    assertEquals(checkSigner(List("p"), "AAAA", signed), Right(()))
    assert(checkSigner(List("p"), "BBBB", signed).left.exists(_.contains("signed by AAAA")))
    assertEquals(checkSigner(List("q"), "BBBB", signed), Right(()))

  test("refusals: no plugin, a foreign SPI, a broken provider, an id taken"):
    val ok = PluginJar("p.jar", "1", List(PluginDescription("p", "1.0", 1)))
    assertEquals(refusals(ok, 1, Map.empty), Nil)
    assert(refusals(PluginJar("e.jar", "1"), 1, Map.empty).head.contains("holds no plugin"))
    assert(refusals(ok, 2, Map.empty).head.contains("SPI 1; this Histmersion speaks SPI 2"))
    assert(refusals(PluginJar("b.jar", "1", List(PluginDescription("", "", 0, error = Some("boom")))), 0, Map.empty)
      .exists(_.contains("could not be loaded: boom")))
    assert(refusals(ok, 1, Map("p" -> "other.jar")).head.contains("already provided by other.jar"))

  test("kit manifest: SHA256SUMS round trip; verify names missing, changed and extra files"):
    val files = List(KitFile("lib/b.jar", "b" * 64), KitFile("classpath", "c" * 64))
    val sums = sha256sums(files)
    assertEquals(sums.linesIterator.toList.head, s"${"c" * 64}  classpath")
    assertEquals(parseSha256sums(sums).map(_.sortBy(_.file)), Right(files.sortBy(_.file)))
    assert(parseSha256sums("zz  x\n").isLeft)
    assertEquals(verifyKit(files, Map("lib/b.jar" -> "b" * 64, "classpath" -> "c" * 64)), Nil)
    assertEquals(verifyKit(files, Map("lib/b.jar" -> "d" * 64, "lib/x.jar" -> "e" * 64)),
      List("missing: classpath", "changed: lib/b.jar", "not in the manifest: lib/x.jar"))
    val k = KitManifest(KitFormat, "0.1.0", Some("abc"), "25.0.1", 1, "com.histmersion.server.Main", files)
    assertEquals(parseKit(renderKit(k)), Right(k))
    assert(parseKit(renderKit(k.copy(format = KitFormat + 1))).isLeft, "a newer kit format is refused")
    assertEquals(k.label, "0.1.0 (abc)")
