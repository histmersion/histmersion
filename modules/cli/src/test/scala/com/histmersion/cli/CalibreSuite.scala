package com.histmersion.cli

import java.nio.file.Path

/** The host-side Calibre integration: both readers produce the same book, the metadata guard refuses junk, and the
  * commands assembled for `calibredb` are the ones we mean to run.
  *
  * The fixtures are REAL output captured from `calibredb list --for-machine` and from `sqlite3 -readonly -json` against
  * the same book, with the home directory rewritten. No Calibre installation is needed to run this.
  */
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
    val viaDb = Calibre.parseMachineJson(fixture("calibredb_machine.json")).fold(fail(_), _.head)
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
    // (empty output is NOT unparseable: it is how sqlite3 says "no rows" — #967, below)
    assert(Calibre.parseSqliteJson("Error: no such table: books", lib).isLeft)

  test("format choice: requested, else pdf, else epub"):
    val b = Calibre
      .parseMachineJson(fixture("calibredb_machine.json"))
      .fold(fail(_), _.head)
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
    val c = Calibre(cfg(), shell)
    val bad = c.add(Path.of("/tmp/whatever.pdf"), CalibrePush("Unknown", List("Unknown Author")))
    assert(bad.isLeft, bad)
    assertEquals(shell.commands, Nil, "the guard must fire before anything is executed")

  test("push refuses while the Calibre GUI holds the library"):
    val shell = RecordingShell(running = true)
    val r = Calibre(cfg(), shell).add(Path.of("/tmp/x.pdf"), CalibrePush("T", List("A")))
    assert(r.left.exists(_.contains("GUI is running")), r)

  /** A shell whose `calibredb list` answers with one book (the entry being updated). */
  private class LibraryShell(onShelf: String) extends RecordingShell():
    override def run(cmd: List[String]) =
      commands = commands :+ cmd
      if cmd.contains("list") then Right(onShelf) else Right("")

  private val meta = CalibrePush(
    "Perspectives on Hittite Civilization",
    List("Güterbock, Hans G.", "Hoffner, Harry A."),
    doi = Some("10.1/x"),
    year = Some(1997),
    publisher = Some("Oriental Institute"),
    summary = Some("our summary"),
    tags = List("hittite")
  )

  private def shelf(
      pubdate: String,
      publisher: String,
      comments: String,
      identifiers: String = "{}",
      tags: String = "[]",
      authors: String = "Hans G. Güterbock"
  ) =
    s"""[{"id":1567,"title":"Kumarbi","authors":"$authors","pubdate":"$pubdate","publisher":"$publisher",""" +
      s""""comments":"$comments","tags":$tags,"identifiers":$identifiers,"formats":[]}]"""

  private def fields(shell: RecordingShell) =
    val cmd = shell.commands.find(_.contains("set_metadata")).getOrElse(fail("no set_metadata ran"))
    assertEquals(cmd.take(5), List("calibredb", "set_metadata", "--with-library", lib, "1567"))
    cmd.drop(5).grouped(2).map { case List("--field", f) => f; case o => fail(s"not a field: $o") }.toList

  test(
    "update writes title, First-Last authors, the record's year, merged identifiers and the topics as tags, and fills publisher and description where Calibre has none"
  ):
    val bare = LibraryShell(shelf("0101-01-01T00:00:00+00:00", "", "-"))
    val changed = Calibre(cfg(), bare).update(1567, meta).map(_.map(_.field))
    assertEquals(
      changed,
      Right(
        List(
          CalibreField.Title,
          CalibreField.Authors,
          CalibreField.Pubdate,
          CalibreField.Identifiers,
          CalibreField.Tags,
          CalibreField.Publisher,
          CalibreField.Comments
        )
      )
    )
    assertEquals(
      fields(bare),
      List(
        "title:Perspectives on Hittite Civilization",
        "authors:Hans G. Güterbock & Harry A. Hoffner",
        "pubdate:1997-01-01",
        "identifiers:doi:10.1/x",
        "tags:hittite",
        "publisher:Oriental Institute",
        "comments:our summary"
      )
    )

  test("update writes the record's year over the edition's (#831), but never its publisher or a store description"):
    val held = LibraryShell(shelf("1986-06-01T00:00:00+00:00", "Gallimard", "The publisher's own blurb"))
    assertEquals(Calibre(cfg(), held).update(1567, meta).map(_.size), Right(5))
    val fs = fields(held)
    assert(fs.contains("pubdate:1997-01-01"), fs)
    assert(!fs.exists(f => f.startsWith("publisher:") || f.startsWith("comments:")), fs)

  test("update MERGES identifiers: the record's DOI is set, Calibre's ISBN and every other scheme are kept"):
    val ids = """{"isbn":"9780918986672","google":"abcDEF","asin":"B000X","doi":"10.9/old"}"""
    val held = LibraryShell(shelf("1997-01-01T00:00:00+00:00", "Oriental Institute", "x", identifiers = ids))
    val _ = Calibre(cfg(), held).update(1567, meta.copy(isbn = Some("9999999999999")))
    assert(fields(held).contains("identifiers:asin:B000X,doi:10.1/x,google:abcDEF,isbn:9780918986672"), fields(held))
    assertEquals(
      Calibre.mergeIdentifiers(Map("google" -> "g"), Some("10.1/x"), Some("978")),
      Map("google" -> "g", "doi" -> "10.1/x", "isbn" -> "978")
    )
    assertEquals(
      Calibre.mergeIdentifiers(Map.empty, Some("10.1/a,b"), None),
      Map.empty[String, String],
      "a comma cannot cross --field identifiers:"
    )

  test("update of an entry that already agrees writes nothing"):
    val same = LibraryShell(
      shelf(
        "1997-01-01T00:00:00+00:00",
        "Oriental Institute",
        "x",
        identifiers = """{"doi":"10.1/x","google":"g"}""",
        tags = """["hittite"]""",
        authors = "Hans G. Güterbock & Harry A. Hoffner"
      ).replace("Kumarbi", "Perspectives on Hittite Civilization")
    )
    assertEquals(Calibre(cfg(), same).update(1567, meta), Right(Nil))
    assert(!same.commands.exists(_.contains("set_metadata")), same.commands)

  // #883: a title differing only by surrounding spaces, or a DOI only by case, is not a change
  test("update compares titles trimmed and DOIs case-insensitively, and writes a title trimmed"):
    val same = LibraryShell(
      shelf(
        "1997-01-01T00:00:00+00:00",
        "Oriental Institute",
        "x",
        identifiers = """{"doi":"10.1/X","google":"g"}""",
        tags = """["hittite"]""",
        authors = "Hans G. Güterbock & Harry A. Hoffner"
      ).replace("Kumarbi", "Perspectives on Hittite Civilization ")
    )
    assertEquals(
      Calibre(cfg(), same).update(1567, meta.copy(title = " Perspectives on Hittite Civilization")),
      Right(Nil)
    )
    assert(!same.commands.exists(_.contains("set_metadata")), same.commands)
    assertEquals(Calibre.mergeIdentifiers(Map("doi" -> "10.1/ABC"), Some("10.1/abc"), None), Map("doi" -> "10.1/ABC"))
    val renamed = LibraryShell(
      shelf(
        "1997-01-01T00:00:00+00:00",
        "Oriental Institute",
        "x",
        identifiers = """{"doi":"10.1/x","google":"g"}""",
        tags = """["hittite"]""",
        authors = "Hans G. Güterbock & Harry A. Hoffner"
      )
    )
    val _ = Calibre(cfg(), renamed).update(1567, meta.copy(title = "  Perspectives on Hittite Civilization  "))
    assertEquals(fields(renamed), List("title:Perspectives on Hittite Civilization"))

  test("authors: First Last, without role words, brackets or library life dates"):
    def n(a: String) = Calibre.normaliseAuthor(a)
    assertEquals(n("Güterbock, Hans G."), "Hans G. Güterbock")
    assertEquals(n("Clunies Ross, Margaret (ed.)"), "Margaret Clunies Ross")
    assertEquals(n("Ken Dowden (ed.)"), "Ken Dowden")
    assertEquals(n("Gimbutienė, Marija (1921-1994)"), "Marija Gimbutienė")
    assertEquals(n("Gimbutas, Marija, 1921-1994"), "Marija Gimbutas")
    assertEquals(n("Nilsson, Martin P., b. 1874"), "Martin P. Nilsson")
    assertEquals(n("Dowden, Ken, eds."), "Ken Dowden")
    assertEquals(n("Hans Güterbock, trans."), "Hans Güterbock")
    assertEquals(n("West, M. L. ed."), "M. L. West")
    assertEquals(n("King, Martin Luther, Jr."), "Martin Luther King Jr.")
    assertEquals(n("Smith, Ed"), "Ed Smith", "a man called Ed is not an editor")
    assertEquals(n("Walter Burkert"), "Walter Burkert")
    assertEquals(Calibre.normaliseAuthors(List("Burkert, Walter", "Walter Burkert (ed.)", " ")), List("Walter Burkert"))

  // ── update --all (#831) ─────────────────────────────────────────────────────────────────────
  private def libraryOf(books: String*) = LibraryShell(books.mkString("[", ",", "]"))
  private def entry(id: Int, title: String, authors: String, pubdate: String) =
    s"""{"id":$id,"title":"$title","authors":"$authors","pubdate":"$pubdate","publisher":"P","comments":"c","tags":["t"],"identifiers":{},"formats":[]}"""
  private val linked = List(
    LinkedRecord(
      "burkert1985_greek",
      10,
      CalibrePush("Greek Religion", List("Burkert, Walter"), year = Some(1985), tags = List("t"))
    ),
    LinkedRecord(
      "page1959_history",
      11,
      CalibrePush("History and the Homeric Iliad", List("Page, Denys"), year = Some(1959), tags = List("t"))
    ),
    LinkedRecord("guterbock1948_kumarbi", 12, CalibrePush("Kumarbi", List("Güterbock, Hans G."), tags = List("t"))),
    LinkedRecord(
      "guterbock1997_perspectives",
      12,
      CalibrePush("Perspectives", List("Güterbock, Hans G."), tags = List("t"))
    ),
    LinkedRecord("gone2000_x", 13, CalibrePush("Gone", List("Someone")))
  )
  private val shelfAll = Seq(
    entry(10, "Greek Religion", "Walter Burkert", "1985-01-01T00:00:00+00:00"),
    entry(11, "History and the Homeric Iliad", "Denys Page", "1963-01-01T00:00:00+00:00"),
    entry(12, "Perspectives", "Hans G. Güterbock", "1997-01-01T00:00:00+00:00")
  )

  test(
    "update --all is a dry run by default: it lists the changes, leaves shared and missing entries alone, writes nothing"
  ):
    val shell = libraryOf(shelfAll*)
    val out = Calibre(cfg(), shell).updateAll(linked, apply = false).fold(fail(_), identity)
    assertEquals(
      out.map(e => e.calibreId -> e.outcome),
      List(
        10 -> UpdateOutcome.UpToDate,
        11 -> UpdateOutcome.Change(List(CalibreChange(CalibreField.Pubdate, "1963", "1959-01-01"))),
        12 -> UpdateOutcome.Shared,
        13 -> UpdateOutcome.Missing
      )
    )
    assertEquals(shell.commands.count(_.contains("list")), 1, "one listing of the library, not one per entry")
    assert(!shell.commands.exists(_.contains("set_metadata")), shell.commands)
    val md = Calibre.markdownUpdateAll(out, applied = false)
    assert(md.contains("| 11 | page1959_history | pubdate | 1963 | 1959-01-01 |"), md)
    assert(md.contains("guterbock1948_kumarbi, guterbock1997_perspectives") && md.contains("--apply"), md)

  test("update --all --apply writes only the entries that change, and refuses while the GUI runs"):
    val shell = libraryOf(shelfAll*)
    val out = Calibre(cfg(), shell).updateAll(linked, apply = true).fold(fail(_), identity)
    assertEquals(
      out.find(_.calibreId == 11).map(_.outcome),
      Some(UpdateOutcome.Applied(List(CalibreChange(CalibreField.Pubdate, "1963", "1959-01-01"))))
    )
    assertEquals(
      shell.commands.filter(_.contains("set_metadata")),
      List(List("calibredb", "set_metadata", "--with-library", lib, "11", "--field", "pubdate:1959-01-01"))
    )
    val gui = RecordingShell(running = true)
    assert(Calibre(cfg(), gui).updateAll(linked, apply = true).left.exists(_.contains("GUI is running")))
    assertEquals(gui.commands, Nil)

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
    assert(
      Calibre(cfg(), RecordingShell()).update(1, CalibrePush("T", List("(ed.)"))).isLeft,
      "a name that is nothing but a role is no author"
    )
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
    val v1 = book(
      1805,
      "The Pre-Christian Religions of the North: Research and Reception, Volume I: From the Middle Ages to c. 1830",
      List("Clunies Ross, Margaret (ed.)"),
      "2018-01-01T00:00:00+00:00"
    )
    val v2 = v1.copy(
      id = 1806,
      title = "The Pre-Christian Religions of the North: Research and Reception, Volume II: From c. 1830 to the Present"
    )
    assertEquals(Calibre.citekeyFor(v1), "cluniesross2018_christian_v1")
    assertEquals(Calibre.citekeyFor(v2), "cluniesross2018_christian_v2")

  test("volume numbers: arabic and roman, and words that merely start with 'vol' are not volumes"):
    assertEquals(Calibre.volumeOf("Prolonged Echoes: Volume 1, The Myths"), Some(1))
    assertEquals(Calibre.volumeOf("Collected Papers, Vol. IV"), Some(4))
    assertEquals(Calibre.volumeOf("vol 12"), Some(12))
    assertEquals(Calibre.volumeOf("Volcanic Winters and the Volvic spring"), None)
    assertEquals(Calibre.volumeOf("The Iliad"), None)

  test("import citekeys transliterate accented letters instead of dropping them (#830)"):
    assertEquals(
      Calibre.citekeyFor(
        book(
          1,
          "Phoenicians and the Making of the Mediterranean",
          List("López-Ruiz, Carolina"),
          "2021-01-01T00:00:00+00:00"
        )
      ),
      "lopezruiz2021_phoenicians"
    )
    assertEquals(
      Calibre.citekeyFor(book(2, "Ženské postavy", List("Běťáková, Marie"), "2012-01-01T00:00:00+00:00")),
      "betakova2012_zenske"
    )
    assertEquals(
      Calibre.citekeyFor(book(3, "Études", List("Øystein Ståhlberg"), "1990-01-01T00:00:00+00:00")),
      "stahlberg1990_etudes"
    )
    assertEquals(
      Calibre.citekeyFor(book(4, "Słowianie", List("Łowmiański, Henryk"), "1963-01-01T00:00:00+00:00")),
      "lowmianski1963_slowianie"
    )
    assertEquals(
      Calibre.citekeyFor(book(5, "Straße", List("Weiß, Ærø"), "2001-01-01T00:00:00+00:00")),
      "weiss2001_strasse"
    )

  test("Calibre's 0101 'no date' is no year at all"):
    val b = book(1, "Twilight of the Gods", List("Ian Young"), "0101-01-01T00:00:00+00:00")
    assertEquals(b.year, None)
    assertEquals(Calibre.citekeyFor(b), "youngnd_twilight")

  test("import refuses to merge a second Calibre entry into an existing record unless --citekey says so"):
    assertEquals(Calibre.importTarget("k", 1806, explicit = false, None), Right(ImportTarget.Create))
    assertEquals(
      Calibre.importTarget("k", 1806, explicit = false, Some(Some("1806"))),
      Right(ImportTarget.Update(None))
    )
    val e = Calibre.importTarget("k", 1806, explicit = false, Some(Some("1805"))).swap.toOption.get
    assert(e.contains("Calibre id 1805") && e.contains("--citekey"), e)
    assert(
      Calibre.importTarget("k", 1806, explicit = false, Some(None)).isLeft,
      "an unlinked record is not merged silently either"
    )
    // #721: an explicit --citekey re-links the record to the imported entry
    assertEquals(
      Calibre.importTarget("k", 1806, explicit = true, Some(Some("1805"))),
      Right(ImportTarget.Update(Some("1805")))
    )
    assertEquals(Calibre.importTarget("k", 1806, explicit = true, Some(None)), Right(ImportTarget.Update(None)))

  // ── push --replace (#652) ───────────────────────────────────────────────────────────────────
  private def tmpFile(ext: String) =
    val f = java.nio.file.Files.createTempFile("calibre-suite-", "." + ext)
    f.toFile.deleteOnExit(); f

  test("replace swaps the format on the linked entry with add_format — never a new entry"):
    val shell = LibraryShell(shelf("1986-06-01T00:00:00+00:00", "", ""))
    val f = tmpFile("pdf")
    assertEquals(Calibre(cfg(), shell).replaceFormat(1567, f), Right("pdf"))
    val cmd = shell.commands.find(_.contains("add_format")).getOrElse(fail("no add_format ran"))
    assertEquals(cmd, List("calibredb", "add_format", "--with-library", lib, "1567", f.toString))
    assert(!shell.commands.exists(c => c.contains("add") && !c.contains("add_format") && c(1) == "add"))

  test("replace refuses a running GUI, a missing entry, and a non-pdf/epub file before writing"):
    val f = tmpFile("pdf")
    val gui = RecordingShell(running = true)
    assert(Calibre(cfg(), gui).replaceFormat(1, f).left.exists(_.contains("GUI is running")))
    assertEquals(gui.commands, Nil)
    val none = LibraryShell("[]")
    assert(Calibre(cfg(), none).replaceFormat(1567, f).left.exists(_.contains("no Calibre entry 1567")))
    assert(!none.commands.exists(_.contains("add_format")))
    val quiet = RecordingShell()
    assert(Calibre(cfg(), quiet).replaceFormat(1, tmpFile("mobi")).isLeft)
    assertEquals(quiet.commands, Nil)

  private def cfg() = com.histmersion.config.CalibreCliConfig(lib, "calibredb", "sqlite3", "")

  /** A [[Shell]] that records instead of executing. */
  private class RecordingShell(running: Boolean = false) extends Shell:
    var commands: List[List[String]] = Nil // a test double is the one place a var is the simplest thing
    def run(cmd: List[String]): Either[String, String] =
      commands = commands :+ cmd
      Right("")
    def isRunning(processMatch: String): Boolean = running

  // ── search: no matches, and the unlinked entries (#967) ─────────────────────────────────────
  test("a search with no match is an empty list, not 'unparseable JSON' — on both readers"):
    assertEquals(Calibre.parseSqliteJson("", lib), Right(Nil))
    assertEquals(Calibre.parseSqliteJson("  \n", lib), Right(Nil))
    assertEquals(Calibre.parseMachineJson(""), Right(Nil))
    assertEquals(Calibre.parseMachineJson("[]"), Right(Nil))
    // the GUI case: calibredb refuses, sqlite3 -json prints nothing at all for an empty result
    val shell = new RecordingShell():
      override def run(cmd: List[String]) =
        commands = commands :+ cmd
        if cmd.head.endsWith("calibredb") then Left("Another calibre program is running") else Right("")
    assertEquals(Calibre(cfg(), shell).search("no such book"), Right(Nil))
    assertEquals(Calibre.markdownSearch(Nil), "_No matching books in the Calibre library._")

  test("unlinked: the entries no source links, newest (highest id) first, limited"):
    val books = List(
      book(3, "Old", List("A"), "2001-01-01"),
      book(9, "Newest", List("B"), "2020-01-01"),
      book(5, "Linked", List("C"), "2010-01-01"),
      book(7, "Middle", List("D"), "2015-01-01")
    )
    assertEquals(Calibre.unlinked(books, Set(5), 10).map(_.id), List(9, 7, 3))
    assertEquals(Calibre.unlinked(books, Set(5), 2).map(_.id), List(9, 7))
    assertEquals(Calibre.unlinked(books, books.map(_.id).toSet, 10), Nil)

  test("unlinked listing: title, authors, year and the file path of each entry"):
    val b =
      book(9, "Newest", List("Kristiansen, K."), "2020-01-01").copy(formats = List(s"$lib/K/Newest (9)/Newest.pdf"))
    val md = Calibre.markdownUnlinked(List(b), total = 3)
    assert(md.contains("1 of 3 Calibre entries not linked"), md)
    assert(md.contains("**9** · Newest (2020) — Kristiansen, K."), md)
    assert(md.contains(s"`$lib/K/Newest (9)/Newest.pdf`"), md)
    assert(md.contains("histmersion calibre import <id>"), md)
    assertEquals(Calibre.markdownUnlinked(Nil, 0), "_Every Calibre entry is linked to a source._")

  test("calibre search takes a query or --unlinked; calibre import takes --acquire-item, but not with --no-convert"):
    val top = com.monovore.decline.Command("histmersion", "test")(CalibreCmd.all)
    def parse(args: String*) = top.parse(args.toList, Map.empty)
    assert(parse("calibre", "search", "troy").isRight)
    assert(parse("calibre", "search", "--unlinked").isRight)
    assert(parse("calibre", "search", "--unlinked", "troy", "--limit", "5").isRight)
    assert(parse("calibre", "search", "").isRight, "an empty query is the whole library")
    assert(parse("calibre", "search").left.exists(_.errors.mkString.contains("--unlinked")))
    assert(parse("calibre", "import", "1693", "--acquire-item", "12").isRight)
    assert(
      parse("calibre", "import", "1693", "--acquire-item", "12", "--no-convert").left.exists(
        _.errors.mkString.contains("drop --no-convert")
      )
    )

  test("#1446: a book's cover file — calibredb's `cover` field, the sqlite fallback's `has_cover`"):
    val b = Calibre
      .parseMachineJson("""[{"id":7,"title":"T","authors":"A","cover":"/lib/A/T (7)/cover.jpg"}]""")
      .fold(fail(_), _.head)
    assertEquals(b.cover, Some("/lib/A/T (7)/cover.jpg"))
    val s = Calibre
      .parseSqliteJson("""[{"id":7,"title":"T","authors":"A","cover":"A/T (7)/cover.jpg"}]""", "/lib")
      .fold(fail(_), _.head)
    assertEquals(s.cover, Some("/lib/A/T (7)/cover.jpg"))
    assertEquals(
      Calibre
        .parseSqliteJson("""[{"id":7,"title":"T","authors":"A","cover":null}]""", "/lib")
        .fold(fail(_), _.head)
        .cover,
      None
    )
    assert(Calibre.sqliteQuery("1=1", 1).contains("has_cover"))

  test("#1446: the Calibre-linked sources `media covers` reads — a citekey quoted, covered ones skipped"):
    val q = MediaCovers.linkedQuery(Some("o'brien2001"), refresh = false)
    assert(q.contains("s.citekey = 'o''brien2001'"), q)
    assert(q.contains("m.role = 'cover'") && q.contains("'^[0-9]+$'"), q)
    assert(!MediaCovers.linkedQuery(None, refresh = true).contains("NOT EXISTS"))

  test("an import into an existing record keeps a compiled summary (#896)"):
    assertEquals(Calibre.importSummary(Some("Calibre blurb (Friedrich et al."), Some("Compiled summary.")), None)
    assertEquals(Calibre.importSummary(Some("Calibre blurb"), None), Some("Calibre blurb"))
    assertEquals(Calibre.importSummary(Some("Calibre blurb"), Some("  ")), Some("Calibre blurb"))
    assertEquals(Calibre.importSummary(None, None), None)
