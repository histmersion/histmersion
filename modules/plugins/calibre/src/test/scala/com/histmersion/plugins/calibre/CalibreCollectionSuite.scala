package com.histmersion.plugins.calibre

import com.histmersion.plugin.*
import com.histmersion.plugin.testkit.{HttpMode, PluginHarness}
import com.histmersion.pluginhost.HostCollection

import java.nio.file.{Files, Path}

/** The `calibre` plugin over a fixture library (a fake `calibredb` over the fixture's books, the system's `sqlite3`
  * over its `metadata.db`): what it reads, what it writes and with which commands, the GUI's lock, write-back off, and
  * what the doctor check says about a library it cannot reach. Never the user's own library
  * ([[CalibreFixture.refuseRealLibrary]]).
  */
class CalibreCollectionSuite extends munit.FunSuite:

  private def scratch(): Path =
    Files.createTempDirectory(Files.createDirectories(Path.of("target/calibre-plugin").toAbsolutePath), "lib-")

  private def library(f: CalibreFixture) =
    CalibreLibrary(f.dir.toString, f.calibredb.toString, f.sqlite3.toString, "/no/such/calibre-gui-process")

  private def collection(f: CalibreFixture, writeBack: Boolean = true) =
    CalibreFixture.refuseRealLibrary(f.dir)
    HostCollection("calibre", CalibreCollection(library(f), writeBack, Shell.System))

  private lazy val master = CalibreFixture.create(scratch().resolve("master"))

  test("the guard refuses the user's own library, however it is spelled"):
    val home = System.getProperty("user.home")
    List("~/Calibre Library", s"$home/Calibre Library", s"$home/./Calibre Library/", s"$home/Calibre Library/sub")
      .foreach(p => intercept[IllegalStateException](CalibreFixture.refuseRealLibrary(Path.of(p))))
    CalibreFixture.refuseRealLibrary(master.dir)

  test("search, one item, its file and cover: the books as the library holds them"):
    val c = collection(master)
    val found = c.search(CollectionQuery(Some("Bronze"), Some(20))).fold(fail(_), identity)
    assertEquals(found.map(_.id.value), List("1", "6"))
    val one = c.item(CollectionId("5")).fold(fail(_), identity).getOrElse(fail("no book 5"))
    assertEquals(
      (one.title, one.authors, one.year.map(y => y.value: Int), one.publisher, one.identifiers),
      (Option("Hittite Myths, Volume II"), List("Harry A. Hoffner"), Option(1998), Option("Scholars Press"),
        Map("isbn" -> "9780788504884"))
    )
    assertEquals(c.item(CollectionId("99")), Right(None))
    assertEquals(c.item(CollectionId("not-a-number")), Right(None))
    val file = c.file(one, Some("epub")).fold(fail(_), identity)
    assert(file.name.endsWith("Book 5.epub"), file)
    assert(c.original(one, file).exists(f => f.extension == "epub" && f.bytes.nonEmpty))
    assert(c.cover(one).exists(_.exists(_.extension == "jpg")))
    val mobi = c.item(CollectionId("7")).toOption.flatten.getOrElse(fail("no book 7"))
    assert(c.file(mobi, None).left.exists(_.contains("has no pdf/epub")), c.file(mobi, None))
    val junk = c.item(CollectionId("4")).toOption.flatten.getOrElse(fail("no book 4"))
    assert(c.admits(junk).left.exists(_.startsWith("Calibre id 4 has empty or Unknown title and author")))
    assertEquals(c.reach, Right(()))
    assertEquals(master.takeLog().count(_.startsWith("calibredb \"list\"")), 5)

  test("the GUI holds the library: reading falls back to sqlite3, read-only"):
    val f = master.copyTo(scratch().resolve("locked"))
    f.lock(true)
    val found = collection(f).search(CollectionQuery(Some("Bronze"), Some(20))).fold(fail(_), identity)
    assertEquals(found.map(_.id.value).sorted, List("1", "6"))
    val calls = f.takeLog()
    assertEquals(calls.map(_.takeWhile(_ != ' ')), List("calibredb", "sqlite3"))
    assert(calls(1).contains("\"-readonly\""), calls)

  test("writes: add, replace, update, set — the commands run on a copy of the library"):
    val f = master.copyTo(scratch().resolve("writes"))
    val c = collection(f)
    val pdf = FetchedFile("original", None, ("%PDF-1.4\n" + "x" * 2048).getBytes("UTF-8"), "pdf")
    val meta = CollectionMetadata("zz_new", "A new book", List("Author New"), doi = Some("10.1/new"), year =
      Some(Year(2001)))
    val id = c.add(meta, pdf).fold(fail(_), identity)
    assertEquals(id.value, "9")
    assertEquals(c.replace(CollectionId("1"), "zz_linked", pdf), Right("pdf"))
    assertEquals(c.update(id, meta.copy(title = "A renamed book")).map(_.map(_.field)), Right(List("title")))
    assertEquals(c.set(CollectionId("1"), List("languages" -> "eng")), Right(()))
    assert(c.set(CollectionId("1"), List("bogus" -> "x")).left.exists(_.startsWith("cannot set bogus")))
    val calls = f.takeLog().map(_.split(' ').take(2).mkString(" "))
    assertEquals(
      calls,
      List(
        "calibredb \"add\"",
        "calibredb \"set_metadata\"",
        "calibredb \"list\"",
        "calibredb \"add_format\"",
        "calibredb \"list\"",
        "calibredb \"set_metadata\"",
        "calibredb \"list\"",
        "calibredb \"set_metadata\""
      )
    )

  test("write-back off: the collection is read-only and every write is refused, naming the switch"):
    val f = master.copyTo(scratch().resolve("readonly"))
    val c = collection(f, writeBack = false)
    assertEquals(c.capabilities, Set(CollectionCapability.Read))
    val pdf = FetchedFile("original", None, Array[Byte](1, 2, 3), "pdf")
    val refusals = List(
      c.add(CollectionMetadata("k", "T", List("A")), pdf),
      c.replace(CollectionId("1"), "k", pdf),
      c.update(CollectionId("1"), CollectionMetadata("k", "T", List("A"))),
      c.updateAll(Nil, apply = true),
      c.set(CollectionId("1"), List("languages" -> "eng"))
    )
    refusals.foreach(r => assert(r.left.exists(_.contains("histmersion.plugins.calibre.write-back = true")), r))
    assertEquals(f.takeLog(), Nil, "nothing ran")
    assert(c.updateAll(Nil, apply = false).isRight, "a dry run reads only")

  test("audit and update-all read the library once"):
    val c = collection(master)
    val recs = List(
      CollectionRecord("zz_tin", CollectionId("1"), "Bronze Age tin and its trade", List("Stocker, Sharon R."), None),
      CollectionRecord("zz_lost", CollectionId("99"), "Lost", List("Nobody"), None)
    )
    val found = c.audit(recs).fold(fail(_), identity)
    assertEquals(found.map(f => f.id.value -> f.issues.map(_.code)), List("99" -> List("Missing")))
    master.takeLog(): Unit
    val links = List(CollectionLink(CollectionId("1"), CollectionMetadata("zz_tin", "Bronze Age tin", List("S"))))
    val plan = c.updateAll(links, apply = false).fold(fail(_), identity)
    assert(plan.head.outcome.isInstanceOf[CollectionUpdateOutcome.Change], plan)
    assertEquals(master.takeLog().size, 1)

  test("the doctor check: a missing library, and one the server may not read"):
    val none = scratch().resolve("absent")
    assert(CalibreCollection.reach(none).left.exists(_.contains("there is no Calibre library at")))
    val empty = Files.createDirectories(scratch().resolve("empty"))
    assert(CalibreCollection.reach(empty).left.exists(_.contains("there is no metadata.db")))
    val locked = Files.createDirectories(scratch().resolve("unreadable"))
    val db = Files.writeString(locked.resolve("metadata.db"), "SQLite format 3")
    Files.setPosixFilePermissions(db, java.nio.file.attribute.PosixFilePermissions.fromString("-w-------"))
    try assert(CalibreCollection.reach(locked).left.exists(_.contains("Full Disk Access")), CalibreCollection.reach(locked))
    finally Files.setPosixFilePermissions(db, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")): Unit

  test("the plugin loads with its defaults — on, writing back — and registers the calibre collection"):
    val h = PluginHarness
      .load("calibre", s"""library-path = "${master.dir}"""", http = HttpMode.Stub(Map.empty), env = Map.empty)
      .fold(e => fail(e), identity)
    assert(h.log.forall(!_.toString.contains("unknown")), h.log)
    val added = List.newBuilder[(String, SourceCollection)]
    val ctx = new PluginContext:
      def http = new PluginHttp { def get(u: String, hs: Map[String, String]) = Left("offline") }
      def log = new PluginLog { def info(m: String) = (); def warn(m: String) = () }
      def env(name: String) = Option.when(name == "HOME")("/home/someone")
      def secret(s: Secret) = Left("none")
      def secretVaults = Registry.refusing("no vaults")
      override def sourceCollections = new Registry[SourceCollection]:
        def add(id: String, a: SourceCollection) = Right(added += id -> a)
    val settings = CalibreSettings("~/Calibre Library", "calibredb", "sqlite3", writeBack = true, "gui")
    assertEquals(CalibrePlugin().register(settings, ctx), Right(()))
    val (id, c) = added.result().headOption.getOrElse(fail("nothing registered"))
    assertEquals((id, c.title, c.capabilities.size), ("calibre", "Calibre", 3))
    assert(c.description.contains("/home/someone/Calibre Library"), c.description)
