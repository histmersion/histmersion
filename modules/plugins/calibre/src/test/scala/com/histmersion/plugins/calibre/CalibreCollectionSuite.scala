package com.histmersion
package plugins.calibre
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.plugin.*
import com.histmersion.plugin.testkit.{HttpMode, PluginHarness}
import com.histmersion.pluginhost.HostCollection

import java.nio.file.{Files, Path}
import com.histmersion.pluginhost.DiskLocalFiles
import com.histmersion.pluginhost.SystemProcesses

/** The `calibre` plugin over a fixture library (a fake `calibredb` over the fixture's books, the system's `sqlite3`
  * over its `metadata.db`): what it reads, what it writes and with which commands, the GUI's lock, write-back off, and
  * what the doctor check says about a library it cannot reach. Never the user's own library
  * ([[CalibreFixture.refuseRealLibrary]]).
  */
class CalibreCollectionSuite extends munit.FunSuite:

  private given LocalFiles = new DiskLocalFiles
  private given Processes = new SystemProcesses
  private def scratch(): Path =
    Files.createTempDirectory(Files.createDirectories(Path.of("target/calibre-plugin").toAbsolutePath), "lib-")

  private def library(f: CalibreFixture) =
    Calibre.Library(
      Calibre.Library.Value(f.dir.toString),
      Calibre.Settings.ExecutablePath(f.calibredb.toString),
      Calibre.Settings.Sqlite(f.sqlite3.toString),
      Shell.ProcessMatch("/no/such/calibre-gui-process")
    )

  private def collection(
      f: CalibreFixture,
      writeBack: CalibreCollection.WriteBack = CalibreCollection.WriteBack(true)
  ) =
    CalibreFixture.refuseRealLibrary(f.dir)
    HostCollection(HostCollection.Name("calibre"), CalibreCollection(library(f), writeBack, Shell.system))

  private lazy val master = CalibreFixture.create(scratch().resolve("master"))

  test("the guard refuses the user's own library, however it is spelled"):
    val home = System.getProperty("user.home")
    List("~/Calibre Library", s"$home/Calibre Library", s"$home/./Calibre Library/", s"$home/Calibre Library/sub")
      .foreach(p => intercept[IllegalStateException](CalibreFixture.refuseRealLibrary(Path.of(p))))
    CalibreFixture.refuseRealLibrary(master.dir)

  test("search, one item, its file and cover: the books as the library holds them"):
    val c = collection(master)
    val found = c
      .search(CollectionQuery(Some(CollectionQuery.Text("Bronze")), Some(CollectionQuery.Limit(20))))
      .fold(e => fail(e.legacyText), identity)
    assertEquals(found.map(_.id.value), List("1", "6"))
    val one = c.item(CollectionId("5")).fold(e => fail(e.legacyText), identity).getOrElse(fail("no book 5"))
    assertEquals(
      (one.title, one.authors, one.year.map(y => y.value: Int), one.publisher, one.identifiers),
      (
        Option("Hittite Myths, Volume II"),
        List("Harry A. Hoffner"),
        Option(1998),
        Option("Scholars Press"),
        Map("isbn" -> "9780788504884")
      )
    )
    assertEquals(c.item(CollectionId("99")), Right(None))
    assertEquals(c.item(CollectionId("not-a-number")), Right(None))
    val file = c.file(one, Some(SourceCollection.FileFormat("epub"))).fold(e => fail(e.legacyText), identity)
    assert(file.name.value.endsWith("Book 5.epub"), file)
    assert(c.original(one, file).exists(f => f.extension.value == "epub" && f.bytes.value.nonEmpty))
    assert(c.cover(one).exists(_.exists(_.extension.value == "jpg")))
    val mobi = c.item(CollectionId("7")).toOption.flatten.getOrElse(fail("no book 7"))
    assert(c.file(mobi, None).left.exists(_.legacyText.contains("has no pdf/epub")), c.file(mobi, None))
    val junk = c.item(CollectionId("4")).toOption.flatten.getOrElse(fail("no book 4"))
    assert(c.admits(junk).left.exists(_.legacyText.startsWith("Calibre id 4 has empty or Unknown title and author")))
    assertEquals(c.reach, Right(()))
    assertEquals(master.takeLog().count(_.startsWith("calibredb \"list\"")), 5)

  test("the GUI holds the library: reading falls back to sqlite3, read-only"):
    val f = master.copyTo(scratch().resolve("locked"))
    f.lock(true)
    val found = collection(f)
      .search(CollectionQuery(Some(CollectionQuery.Text("Bronze")), Some(CollectionQuery.Limit(20))))
      .fold(e => fail(e.legacyText), identity)
    assertEquals(found.map(_.id.value).sorted, List("1", "6"))
    val calls = f.takeLog()
    assertEquals(calls.map(_.takeWhile(_ != ' ')), List("calibredb", "sqlite3"))
    assert(calls(1).contains("\"-readonly\""), calls)

  test("writes: add, replace, update, set — the commands run on a copy of the library"):
    val f = master.copyTo(scratch().resolve("writes"))
    val c = collection(f)
    val pdf = FetchedFile(
      FetchedFile.Source("original"),
      None,
      FetchedFile.Bytes(("%PDF-1.4\n" + "x" * 2048).getBytes("UTF-8")),
      FetchedFile.Extension("pdf")
    )
    val meta =
      CollectionMetadata(
        CollectionMetadata.Key("zz_new"),
        CollectionMetadata.Title("A new book"),
        List(CollectionMetadata.Authors("Author New")),
        doi = Some(CollectionMetadata.Doi("10.1/new")),
        year = Some(Year(2001))
      )
    val id = c.add(meta, pdf).fold(e => fail(e.legacyText), identity)
    assertEquals(id.value, "9")
    assertEquals(c.replace(CollectionId("1"), SourceCollection.ReplaceKey("zz_linked"), pdf), Right("pdf"))
    assertEquals(
      c.update(id, meta.copy(title = CollectionMetadata.Title("A renamed book"))).map(_.map(_.field)),
      Right(List("title"))
    )
    assertEquals(
      c.set(
        CollectionId("1"),
        List(SourceCollection.FieldName("languages") -> SourceCollection.FieldValue("eng"))
      ),
      Right(())
    )
    assert(
      c.set(CollectionId("1"), List(SourceCollection.FieldName("bogus") -> SourceCollection.FieldValue("x")))
        .left
        .exists(_.legacyText.startsWith("cannot set bogus"))
    )
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
    val c = collection(f, writeBack = CalibreCollection.WriteBack(false))
    assertEquals(c.capabilities, Set(CollectionCapability.Read))
    val pdf = FetchedFile(
      FetchedFile.Source("original"),
      None,
      FetchedFile.Bytes(Array[Byte](1, 2, 3)),
      FetchedFile.Extension("pdf")
    )
    val refusals = List(
      c.add(
        CollectionMetadata(
          CollectionMetadata.Key("k"),
          CollectionMetadata.Title("T"),
          List(CollectionMetadata.Authors("A"))
        ),
        pdf
      ),
      c.replace(CollectionId("1"), SourceCollection.ReplaceKey("k"), pdf),
      c.update(
        CollectionId("1"),
        CollectionMetadata(
          CollectionMetadata.Key("k"),
          CollectionMetadata.Title("T"),
          List(CollectionMetadata.Authors("A"))
        )
      ),
      c.updateAll(Nil, apply = SourceCollection.UpdateAllApply(true)),
      c.set(
        CollectionId("1"),
        List(SourceCollection.FieldName("languages") -> SourceCollection.FieldValue("eng"))
      )
    )
    refusals.foreach(r =>
      assert(r.left.exists(_.legacyText.contains("histmersion.plugins.calibre.write-back = true")), r)
    )
    assertEquals(f.takeLog(), Nil, "nothing ran")
    assert(c.updateAll(Nil, apply = SourceCollection.UpdateAllApply(false)).isRight, "a dry run reads only")

  test("audit and update-all read the library once"):
    val c = collection(master)
    val recs = List(
      CollectionRecord(
        CollectionRecord.Key("zz_tin"),
        CollectionId("1"),
        CollectionRecord.Title("Bronze Age tin and its trade"),
        List(CollectionRecord.Authors("Stocker, Sharon R.")),
        None
      ),
      CollectionRecord(
        CollectionRecord.Key("zz_lost"),
        CollectionId("99"),
        CollectionRecord.Title("Lost"),
        List(CollectionRecord.Authors("Nobody")),
        None
      )
    )
    val found = c.audit(recs).fold(e => fail(e.legacyText), identity)
    assertEquals(found.map(f => f.id.value -> f.issues.map(_.code)), List("99" -> List("Missing")))
    master.takeLog(): Unit
    val links = List(
      CollectionLink(
        CollectionId("1"),
        CollectionMetadata(
          CollectionMetadata.Key("zz_tin"),
          CollectionMetadata.Title("Bronze Age tin"),
          List(CollectionMetadata.Authors("S"))
        )
      )
    )
    val plan =
      c.updateAll(links, apply = SourceCollection.UpdateAllApply(false)).fold(e => fail(e.legacyText), identity)
    assert(plan.head.outcome.isInstanceOf[CollectionUpdateOutcome.Change], plan)
    assertEquals(master.takeLog().size, 1)

  test("the doctor check: a missing library, and one the server may not read"):
    val none = scratch().resolve("absent")
    assert(CalibreCollection.reach(none).left.exists(_.legacyText.contains("there is no Calibre library at")))
    val empty = Files.createDirectories(scratch().resolve("empty"))
    assert(CalibreCollection.reach(empty).left.exists(_.legacyText.contains("there is no metadata.db")))
    val locked = Files.createDirectories(scratch().resolve("unreadable"))
    val db = Files.writeString(locked.resolve("metadata.db"), "SQLite format 3")
    Files.setPosixFilePermissions(db, java.nio.file.attribute.PosixFilePermissions.fromString("-w-------"))
    try
      assert(
        CalibreCollection.reach(locked).left.exists(_.legacyText.contains("Full Disk Access")),
        CalibreCollection.reach(locked)
      )
    finally
      Files.setPosixFilePermissions(db, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")): Unit

  test("the plugin loads with its defaults — on, writing back — and registers the calibre collection"):
    val h = PluginHarness
      .load(
        Plugin.Info.Id("calibre"),
        s"""library-path = "${master.dir}"""",
        http = HttpMode.Stub(Map.empty),
        env = Map.empty
      )
      .fold(e => fail(e.legacyText), identity)
    assert(h.log.forall(!_.toString.contains("unknown")), h.log)
    val added = List.newBuilder[(String, SourceCollection)]
    val ctx = new Plugin.Context:
      override def files: LocalFiles = summon[LocalFiles]
      override def processes: Processes = summon[Processes]
      def http = new Plugin.Http {
        def get(u: Plugin.Http.Url, hs: Map[Header.Name, Header.Value]) =
          Left(Plugin.Http.Failure.Transport(Problem.Unclassified(Legacy.Text("offline"))))
      }
      def log = new Plugin.Log { def info(m: Plugin.Log.Line) = (); def warn(m: Plugin.Log.Line) = () }
      def env(name: EnvVar.Name) =
        Option.when(name.value == "HOME")(EnvVar.Value("/home/someone"))
      def secret(s: Secret) = Left(Problem.Unclassified(Legacy.Text("none")))
      def secretVaults = Registry.refusing(Problem.Unclassified(Legacy.Text("no vaults")))
      override def sourceCollections = new Registry[SourceCollection]:
        def add(id: Registry.AddId, a: SourceCollection) = Right(added += id.value -> a)
    val settings = Calibre.Settings(
      Calibre.Settings.LibraryPath("~/Calibre Library"),
      Calibre.Settings.Calibredb("calibredb"),
      Calibre.Settings.Sqlite("sqlite3"),
      writeBack = Calibre.Settings.WriteBack(true),
      Calibre.Settings.GuiProcess("gui")
    )
    assertEquals(CalibrePlugin().register(settings, ctx), Right(()))
    val (id, c) = added.result().headOption.getOrElse(fail("nothing registered"))
    assertEquals((id, c.title, c.capabilities.size), ("calibre", "Calibre", 3))
    assert(c.description.value.contains("/home/someone/Calibre Library"), c.description)
