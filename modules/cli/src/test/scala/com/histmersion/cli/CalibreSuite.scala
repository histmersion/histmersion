package com.histmersion.cli

import java.nio.file.Path

/** The host-side Calibre integration: both readers produce the same book, the metadata guard refuses
  * junk, and the commands assembled for `calibredb` are the ones we mean to run.
  *
  * The fixtures are REAL output captured from `calibredb list --for-machine` and from
  * `sqlite3 -readonly -json` against the same book, with the home directory rewritten. No Calibre
  * installation is needed to run this. */
class CalibreSuite extends munit.FunSuite:

  private def fixture(name: String) = scala.io.Source.fromResource(s"acquire/$name").mkString

  private val lib = "/Users/example/Calibre Library"

  // ── the two readers agree ───────────────────────────────────────────────────────────────────
  test("calibredb --for-machine JSON → CalibreBook"):
    val b = Calibre.parseMachineJson(fixture("calibredb_machine.json")).fold(fail(_), _.head)
    assertEquals(b.id, 1691)
    assertEquals(b.title.take(22), "Likelihood calculation")
    assertEquals(b.authors, List("Philipp Rönchen", "Tilo Wiklund", "Harald Hammarström"))
    assertEquals(b.year, Some(2024))
    assertEquals(b.publisher, Some("Language Dynamics and Change"))
    assertEquals(b.doi, Some("10.1163/22105832-bja10032"))
    assert(b.tags.contains("phylogenetics"))
    assert(b.comments.exists(_.contains("likelihood algorithm")))
    assertEquals(b.formats.size, 1)
    assert(b.formats.head.startsWith(lib), b.formats.head)

  test("the sqlite3 fallback yields the SAME book (it is the path used while the GUI is open)"):
    val viaDb   = Calibre.parseMachineJson(fixture("calibredb_machine.json")).fold(fail(_), _.head)
    val viaFile = Calibre.parseSqliteJson(fixture("calibre_sqlite.json"), lib).fold(fail(_), _.head)
    assertEquals(viaFile.id, viaDb.id)
    assertEquals(viaFile.title, viaDb.title)
    assertEquals(viaFile.authors, viaDb.authors)
    assertEquals(viaFile.year, viaDb.year)
    assertEquals(viaFile.publisher, viaDb.publisher)
    assertEquals(viaFile.doi, viaDb.doi)
    assertEquals(viaFile.tags.toSet, viaDb.tags.toSet)
    // sqlite stores paths relative to the library root; the reader absolutises them
    assertEquals(viaFile.formats, viaDb.formats)

  test("unparseable output is an error, not an exception"):
    assert(Calibre.parseMachineJson("Another calibre program is running").isLeft)
    assert(Calibre.parseSqliteJson("", lib).isLeft)

  test("format choice: requested, else pdf, else epub"):
    val b = Calibre.parseMachineJson(fixture("calibredb_machine.json")).fold(fail(_), _.head)
      .copy(formats = List("/lib/x.epub", "/lib/x.pdf"))
    assertEquals(b.pick(None).map(_.takeRight(4)), Some(".pdf"))
    assertEquals(b.pick(Some("epub")).map(_.takeRight(5)), Some(".epub"))
    assertEquals(b.copy(formats = List("/lib/x.mobi")).pick(None), None)

  // ── the metadata guard ──────────────────────────────────────────────────────────────────────
  test("the metadata guard refuses empty and Unknown title/author"):
    assert(Calibre.guard("A Real Title", List("A Real Author")).isRight)
    assert(Calibre.guard("", List("Author")).isLeft)
    assert(Calibre.guard("Untitled", List("Author")).isLeft)
    assert(Calibre.guard("Title", Nil).isLeft)
    assert(Calibre.guard("Title", List("Unknown Author")).isLeft)
    val both = Calibre.guard("unknown", List("  ")).left.getOrElse("")
    assert(both.contains("title") && both.contains("author"), both)

  test("blankness is case- and whitespace-insensitive"):
    assert(Calibre.isBlank("  UNKNOWN  "))
    assert(Calibre.isBlank("n/a"))
    assert(!Calibre.isBlank("Unknown Pleasures")) // a real title that merely starts with the word

  // ── command assembly (nothing is executed) ──────────────────────────────────────────────────
  test("`calibredb add` reports the new id"):
    assertEquals(Calibre.parseAddedId("Added book ids: 1693\n"), Some(1693))
    assertEquals(Calibre.parseAddedId("Added book ids: 1693,1694"), Some(1693))
    assertEquals(Calibre.parseAddedId("nothing happened"), None)

  test("SQL literals are escaped, so a quote in a search term cannot break the query"):
    assertEquals(Calibre.sqlLiteral("O'Brien"), "'O''Brien'")
    val q = Calibre.sqliteQuery(s"b.title LIKE ${Calibre.sqlLiteral("%O'Brien%")}", 5)
    assert(q.contains("'%O''Brien%'"), q)
    assert(q.contains("LIMIT 5"))
    assert(!q.contains("\n"))

  test("push refuses junk metadata and never reaches calibredb"):
    val shell = RecordingShell()
    val c     = Calibre(cfg(), shell)
    val bad   = c.add(Path.of("/tmp/whatever.pdf"), CalibrePush("Unknown", List("Unknown Author")))
    assert(bad.isLeft, bad)
    assertEquals(shell.commands, Nil, "the guard must fire before anything is executed")

  test("push refuses while the Calibre GUI holds the library"):
    val shell = RecordingShell(running = true)
    val r     = Calibre(cfg(), shell).add(Path.of("/tmp/x.pdf"), CalibrePush("T", List("A")))
    assert(r.left.exists(_.contains("GUI is running")), r)

  /** A shell whose `calibredb list` answers with one book (the entry being updated). */
  private class LibraryShell(onShelf: String) extends RecordingShell():
    override def run(cmd: List[String]) =
      commands = commands :+ cmd
      if cmd.contains("list") then Right(onShelf) else Right("")

  private val meta = CalibrePush("Perspectives on Hittite Civilization", List("Güterbock, Hans G.", "Hoffner, Harry A."),
    doi = Some("10.1/x"), year = Some(1997), publisher = Some("Oriental Institute"),
    summary = Some("our summary"), tags = List("hittite"))

  private def shelf(pubdate: String, publisher: String, comments: String) =
    s"""[{"id":1567,"title":"Kumarbi","authors":"Hans G. Güterbock","pubdate":"$pubdate","publisher":"$publisher",""" +
      s""""comments":"$comments","tags":[],"identifiers":{},"formats":[]}]"""

  private def fields(shell: RecordingShell) =
    val cmd = shell.commands.find(_.contains("set_metadata")).getOrElse(fail("no set_metadata ran"))
    assertEquals(cmd.take(5), List("calibredb", "set_metadata", "--with-library", lib, "1567"))
    assert(!cmd.exists(_.startsWith("identifiers:")) && !cmd.exists(_.startsWith("tags:")), cmd)
    cmd.drop(5).grouped(2).map { case List("--field", f) => f; case o => fail(s"not a field: $o") }.toList

  test("update rewrites title and authors, and fills publisher, year and description only where Calibre has none"):
    val bare = LibraryShell(shelf("0101-01-01T00:00:00+00:00", "", "-"))
    assertEquals(Calibre(cfg(), bare).update(1567, meta), Right(()))
    assertEquals(fields(bare), List("title:Perspectives on Hittite Civilization",
      "authors:Güterbock, Hans G. & Hoffner, Harry A.", "publisher:Oriental Institute", "pubdate:1997-01-01",
      "comments:our summary"))

  test("update never replaces the edition's year, its publisher or a store description"):
    val held = LibraryShell(shelf("1986-06-01T00:00:00+00:00", "Gallimard", "The publisher's own blurb"))
    assertEquals(Calibre(cfg(), held).update(1567, meta), Right(()))
    assertEquals(fields(held), List("title:Perspectives on Hittite Civilization",
      "authors:Güterbock, Hans G. & Hoffner, Harry A."))

  test("update of an id Calibre does not have is an error, and nothing is written"):
    val none = LibraryShell("[]")
    assert(Calibre(cfg(), none).update(1567, meta).isLeft)
    assert(!none.commands.exists(_.contains("set_metadata")))

  test("set writes only the named, settable fields, and refuses junk before running anything"):
    val shell = LibraryShell(shelf("1986-06-01T00:00:00+00:00", "Gallimard", "blurb"))
    assertEquals(Calibre(cfg(), shell).set(1567, List("languages" -> "fra", "pubdate" -> "1968-01-01")), Right(()))
    assertEquals(fields(shell), List("languages:fra", "pubdate:1968-01-01"))
    val quiet = RecordingShell()
    assert(Calibre(cfg(), quiet).set(1, List("formats" -> "x")).left.exists(_.contains("cannot set formats")))
    assert(Calibre(cfg(), quiet).set(1, List("authors" -> "Walter Burkert & Unknown")).isLeft)
    assert(Calibre(cfg(), quiet).set(1, Nil).isLeft)
    assertEquals(quiet.commands, Nil)

  test("update refuses junk metadata and a running GUI before running anything"):
    val shell = RecordingShell()
    assert(Calibre(cfg(), shell).update(1, CalibrePush("Unknown", List("A"))).isLeft)
    assertEquals(shell.commands, Nil)
    val gui = RecordingShell(running = true)
    assert(Calibre(cfg(), gui).update(1, CalibrePush("T", List("A"))).left.exists(_.contains("GUI is running")))
    assertEquals(gui.commands, Nil)

  test("reading falls back to sqlite3 when calibredb refuses (the GUI case)"):
    val shell = new RecordingShell():
      override def run(cmd: List[String]) =
        commands = commands :+ cmd
        if cmd.head.endsWith("calibredb") then Left("Another calibre program is running")
        else Right(scala.io.Source.fromResource("acquire/calibre_sqlite.json").mkString)
    val books = Calibre(cfg(), shell).search("likelihood")
    assertEquals(books.map(_.map(_.id)), Right(List(1691)))
    assertEquals(shell.commands.size, 2, "calibredb first, then sqlite3")
    assert(shell.commands(1).contains("-readonly"), "the metadata.db must be opened READ-ONLY")

  // ── import: citekeys (#714) ─────────────────────────────────────────────────────────────────
  private def book(id: Int, title: String, authors: List[String], pubdate: String) =
    CalibreBook(id, title, authors, Some(pubdate), None, Nil, None, Map.empty, Nil)

  test("two volumes of one work get two citekeys, with the whole surname"):
    val v1 = book(1805, "The Pre-Christian Religions of the North: Research and Reception, Volume I: From the Middle Ages to c. 1830",
      List("Clunies Ross, Margaret (ed.)"), "2018-01-01T00:00:00+00:00")
    val v2 = v1.copy(id = 1806, title = "The Pre-Christian Religions of the North: Research and Reception, Volume II: From c. 1830 to the Present")
    assertEquals(Calibre.citekeyFor(v1), "cluniesross2018_christian_v1")
    assertEquals(Calibre.citekeyFor(v2), "cluniesross2018_christian_v2")

  test("volume numbers: arabic and roman, and words that merely start with 'vol' are not volumes"):
    assertEquals(Calibre.volumeOf("Prolonged Echoes: Volume 1, The Myths"), Some(1))
    assertEquals(Calibre.volumeOf("Collected Papers, Vol. IV"), Some(4))
    assertEquals(Calibre.volumeOf("vol 12"), Some(12))
    assertEquals(Calibre.volumeOf("Volcanic Winters and the Volvic spring"), None)
    assertEquals(Calibre.volumeOf("The Iliad"), None)

  test("Calibre's 0101 'no date' is no year at all"):
    val b = book(1, "Twilight of the Gods", List("Ian Young"), "0101-01-01T00:00:00+00:00")
    assertEquals(b.year, None)
    assertEquals(Calibre.citekeyFor(b), "youngnd_twilight")

  test("import refuses to merge a second Calibre entry into an existing record unless --citekey says so"):
    assertEquals(Calibre.importTarget("k", 1806, explicit = false, None), Right(ImportTarget.Create))
    assertEquals(Calibre.importTarget("k", 1806, explicit = false, Some(Some("1806"))), Right(ImportTarget.Update(None)))
    val e = Calibre.importTarget("k", 1806, explicit = false, Some(Some("1805"))).swap.toOption.get
    assert(e.contains("Calibre id 1805") && e.contains("--citekey"), e)
    assert(Calibre.importTarget("k", 1806, explicit = false, Some(None)).isLeft, "an unlinked record is not merged silently either")
    // #721: an explicit --citekey re-links the record to the imported entry
    assertEquals(Calibre.importTarget("k", 1806, explicit = true, Some(Some("1805"))), Right(ImportTarget.Update(Some("1805"))))
    assertEquals(Calibre.importTarget("k", 1806, explicit = true, Some(None)), Right(ImportTarget.Update(None)))

  // ── push --replace (#652) ───────────────────────────────────────────────────────────────────
  private def tmpFile(ext: String) =
    val f = java.nio.file.Files.createTempFile("calibre-suite-", "." + ext)
    f.toFile.deleteOnExit(); f

  test("replace swaps the format on the linked entry with add_format — never a new entry"):
    val shell = LibraryShell(shelf("1986-06-01T00:00:00+00:00", "", ""))
    val f     = tmpFile("pdf")
    assertEquals(Calibre(cfg(), shell).replaceFormat(1567, f), Right("pdf"))
    val cmd = shell.commands.find(_.contains("add_format")).getOrElse(fail("no add_format ran"))
    assertEquals(cmd, List("calibredb", "add_format", "--with-library", lib, "1567", f.toString))
    assert(!shell.commands.exists(c => c.contains("add") && !c.contains("add_format") && c(1) == "add"))

  test("replace refuses a running GUI, a missing entry, and a non-pdf/epub file before writing"):
    val f   = tmpFile("pdf")
    val gui = RecordingShell(running = true)
    assert(Calibre(cfg(), gui).replaceFormat(1, f).left.exists(_.contains("GUI is running")))
    assertEquals(gui.commands, Nil)
    val none = LibraryShell("[]")
    assert(Calibre(cfg(), none).replaceFormat(1567, f).left.exists(_.contains("no Calibre entry 1567")))
    assert(!none.commands.exists(_.contains("add_format")))
    val quiet = RecordingShell()
    assert(Calibre(cfg(), quiet).replaceFormat(1, tmpFile("mobi")).isLeft)
    assertEquals(quiet.commands, Nil)

  private def cfg() = com.histmersion.core.CalibreCliConfig(lib, "calibredb", "sqlite3", "")

  /** A [[Shell]] that records instead of executing. */
  private class RecordingShell(running: Boolean = false) extends Shell:
    var commands: List[List[String]] = Nil // a test double is the one place a var is the simplest thing
    def run(cmd: List[String]): Either[String, String] =
      commands = commands :+ cmd
      Right("")
    def isRunning(processMatch: String): Boolean = running
