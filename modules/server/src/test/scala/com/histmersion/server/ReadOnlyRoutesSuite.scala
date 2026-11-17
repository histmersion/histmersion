package com.histmersion
package server

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/** No GET or HEAD route writes (the owner's decision of 2026-11-17, S1 follow-up 5): the access gate leaves a
  * cross-site read unchecked because a read changes nothing, and this is what holds it to that. Read from the compiled
  * trees of every main module ([[RouteWrites]]): each route given its logic by `serverLogic…`, its method from its
  * endpoint's definition, and every write its logic's call graph reaches.
  *
  * The routes that write today are listed in `architecture/get-writes.tsv`, exact both ways (a new one fails, a row no
  * route matches any more fails); `HISTMERSION_UPDATE_ARCHITECTURE=1` rewrites it, and refuses while the list grows.
  * `HISTMERSION_GET_WRITES_REPORT=<file>` also writes each write's chain of calls there. A route whose method the check
  * cannot read fails the suite: it could not vouch for it.
  */
class ReadOnlyRoutesSuite extends munit.FunSuite:
  override val munitTimeout: Duration = 20.minutes

  /** The suite's class path: its class loaders' (sbt's test runner loads the suite with one of its own), else the
    * JVM's.
    */
  private val classpath: List[String] =
    val loaders = Iterator.iterate(getClass.getClassLoader)(_.getParent).takeWhile(_ != null).toList
    val urls = loaders.collect { case u: java.net.URLClassLoader => u.getURLs.toList }.flatten
    val fromLoaders = urls.filter(_.getProtocol == "file").map(u => Path.of(u.toURI).toString)
    val fromJvm = System.getProperty("java.class.path").split(File.pathSeparator).toList.filter(_.nonEmpty)
    (fromLoaders ++ fromJvm).distinct

  /** The main modules' compiled code on the class path: their jars, or their class directories. */
  private val mainCode: List[String] =
    classpath.filter { entry =>
      val name = Path.of(entry).getFileName.toString
      val module = entry.contains("/histmersion-") || name.startsWith("histmersion-")
      val test = name.endsWith("-tests.jar") || entry.contains("test-classes")
      val demo = entry.contains("plugin-hello") || entry.contains("plugin-testkit")
      module && !test && !demo && (name.endsWith(".jar") || Files.isDirectory(Path.of(entry)))
    }

  private def tastyIn(dir: String): List[String] =
    val stream = Files.walk(Path.of(dir))
    try stream.iterator().asScala.filter(_.toString.endsWith(".tasty")).map(_.toString).toList
    finally stream.close()

  private def scanned(code: List[String]): RouteWrites.Found =
    val (jars, dirs) = code.partition(_.endsWith(".jar"))
    RouteWrites.scan(dirs.flatMap(tastyIn), jars, classpath)

  /** The fixture's own TASTy file, and the root it is read from: copied out of the test jar when the tests run from
    * one.
    */
  private def fixture: (String, String) =
    val resource = "com/histmersion/server/ReadOnlyRoutesFixture.tasty"
    val url = getClass.getClassLoader.getResource(resource)
    assert(url != null, s"$resource is not on the class path")
    if url.getProtocol == "file" then
      val file = Path.of(url.toURI)
      (file.toString, Iterator.iterate(file)(_.getParent).drop(4).next().toString)
    else
      val root = Files.createTempDirectory("hm-get-writes")
      val out = root.resolve(resource)
      val _ = Files.createDirectories(out.getParent)
      val in = url.openStream()
      try { val _ = Files.copy(in, out) }
      finally in.close()
      (out.toString, root.toString)

  /** The db module's code, for the read-only transaction the fixture opens. */
  private def dbModule: List[String] = mainCode.filter(_.contains("histmersion-db"))

  test("the check finds a planted GET's write however it is reached, and only a GET's or HEAD's"):
    val (jars, dirs) = dbModule.partition(_.endsWith(".jar"))
    val (file, root) = fixture
    val found = RouteWrites.scan(file :: dirs.flatMap(tastyIn), jars, root :: classpath)
    assert(found.unread.isEmpty, s"compiled files the scan could not read: ${found.unread.mkString("\n")}")
    val fixtureWrites = found.writes.filter(_.route.site.contains("ReadOnlyRoutesFixture"))
    val flagged =
      fixtureWrites.map(w => (w.route.endpoint.split('.').last, w.route.method, w.kind)).distinct.sortBy(_.toString)
    assertEquals(
      flagged,
      List(
        ("anyMethod", "ANY", RouteWrites.Kind.File),
        ("derived", "GET", RouteWrites.Kind.File),
        ("direct", "GET", RouteWrites.Kind.File),
        ("dispatched", "GET", RouteWrites.Kind.File),
        ("functionValued", "GET", RouteWrites.Kind.File),
        ("headed", "HEAD", RouteWrites.Kind.Process),
        ("rolledBack", "GET", RouteWrites.Kind.Database),
        ("updating", "GET", RouteWrites.Kind.Database)
      ).sortBy(_.toString),
      fixtureWrites.map(w => s"${w.row}\n  ${w.via.mkString("\n  ")}").mkString("\n")
    )
    // the POST's write is no finding; the reading GET and the read-only transaction write nothing
    val methods =
      found.routes.filter(_.site.contains("ReadOnlyRoutesFixture")).map(r => r.endpoint.split('.').last -> r.method)
    assert(methods.contains("posting" -> "POST"), methods)
    assert(methods.contains("reading" -> "GET") && methods.contains("readOnly" -> "GET"), methods)
    assert(methods.contains("setting" -> "GET"), "a SET statement run through update is no write: " + methods)
    // the dispatched write is found through the implementation that overrides the trait's method
    val dispatched = fixtureWrites.find(_.route.endpoint.endsWith("dispatched")).get
    assert(dispatched.via.exists(_.contains("FileStore.save")), dispatched.via)
    // an endpoint handed in as a parameter cannot be vouched for
    val unresolved = found.unresolved.filter(_.site.contains("ReadOnlyRoutesFixture"))
    assertEquals(unresolved.size, 1, unresolved)
    assert(unresolved.head.endpoint.endsWith("e"), unresolved)

  test("no GET or HEAD route writes but the listed ones, and every listed one still does"):
    assert(mainCode.exists(_.contains("histmersion-server")), s"the server's code is not on the class path: $classpath")
    val found = scanned(mainCode)
    assert(found.unread.isEmpty, s"compiled files the scan could not read: ${found.unread.mkString("\n")}")
    assert(found.routes.size > 200, s"only ${found.routes.size} routes found in ${found.trees} trees")
    assert(
      found.unresolved.isEmpty,
      s"routes whose method the check cannot read: ${found.unresolved.mkString("\n")}"
    )
    sys.env.get("HISTMERSION_GET_WRITES_REPORT").filter(_.nonEmpty).foreach { out =>
      val reads = found.routes.filter(_.reads)
      val head = s"# ${found.trees} trees, ${found.routes.size} routes, ${reads.size} GET/HEAD/ANY, " +
        s"${found.writes.map(_.route).distinct.size} of them write"
      val text = (head :: found.writes.map(w => s"${w.row}\n  ${w.via.mkString("\n  ")}")).mkString("\n")
      val _ = Files.writeString(Path.of(out), text + "\n", UTF_8)
    }
    val file = ArchitectureSuite.Resources.resolve("get-writes.tsv")
    val actual = found.writes.map(_.row).distinct.sorted
    val listed =
      if Files.isRegularFile(file) then Files.readAllLines(file, UTF_8).asScala.filterNot(_.startsWith("#")).toList
      else Nil
    if sys.env.get("HISTMERSION_UPDATE_ARCHITECTURE").contains("1") then
      val added = actual.filterNot(listed.toSet)
      assert(
        added.isEmpty || !Files.isRegularFile(file),
        s"not rewritten: a GET or HEAD route writes that the list does not name (fix it first): ${added.mkString("\n")}"
      )
      val header = "# endpoint\tmethod\troute file\twrite\tsink — GET/HEAD routes that write today; shrink, never grow"
      val _ = Files.write(file, (header :: actual).asJava, UTF_8)
    else
      val added = actual.filterNot(listed.toSet)
      val gone = listed.filterNot(actual.toSet)
      val via = found.writes.groupBy(_.row).view.mapValues(_.head.via.mkString(" → ")).toMap
      assert(
        added.isEmpty && gone.isEmpty,
        (added.map(r => s"a GET or HEAD route writes: $r\n  via ${via(r)}") ++
          gone.map(r => s"no longer written — remove it from architecture/get-writes.tsv: $r")).mkString("\n")
      )
