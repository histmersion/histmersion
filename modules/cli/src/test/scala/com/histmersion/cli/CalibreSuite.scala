package com.histmersion.cli

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Api
import com.histmersion.api.library.{AuthorName, Citekey, CollectionEntry, CollectionFieldValue, CollectionSync, Doi, Source}
import com.histmersion.config.CalibreCliConfig

import java.nio.file.Path

/** The host-side Calibre integration: both readers produce the same book, the metadata guard refuses junk, and the
  * commands assembled for `calibredb` are the ones we mean to run.
  *
  * The fixtures are REAL output captured from `calibredb list --for-machine` and from `sqlite3 -readonly -json` against
  * the same book, with the home directory rewritten. No Calibre installation is needed to run this.
  */
class CalibreSuite extends munit.FunSuite:

  private given console: com.histmersion.util.console.Console = com.histmersion.util.console.Console.standard

  private def fixture(name: String) = scala.io.Source.fromResource(s"acquire/$name").mkString

  private val lib = CalibreCliConfig.LibraryDir("/Users/example/Calibre Library")

  // ── the two readers agree ───────────────────────────────────────────────────────────────────
  test("calibredb --for-machine JSON → CalibreBook"):
    val b = Calibre.parseMachineJson(fixture("calibredb_machine.json")).fold(p => fail(p.value), _.head)
    assertEquals(b.id, Calibre.Book.Id(1691))
    assertEquals(b.title.value.take(22), "Likelihood calculation")
    assertEquals(
      b.authors,
      List(
        Calibre.Book.Authors("Philipp Rönchen"),
        Calibre.Book.Authors("Tilo Wiklund"),
        Calibre.Book.Authors("Harald Hammarström")
      )
    )
    assertEquals(b.year, Some(Calibre.Book.Year(2024)))
    assertEquals(b.publisher, Some(Calibre.Book.Publisher("Language Dynamics and Change")))
    assertEquals(b.doi, Some(Doi("10.1163/22105832-bja10032")))
    assert(b.tags.contains(Calibre.Book.Tags("phylogenetics")))
    assert(b.comments.exists(_.value.contains("likelihood algorithm")))
    assertEquals(b.formats.size, 1)
    assert(b.formats.head.value.startsWith(lib.value), b.formats.head)

  test("the sqlite3 fallback yields the SAME book (it is the path used while the GUI is open)"):
    val viaDb = Calibre.parseMachineJson(fixture("calibredb_machine.json")).fold(p => fail(p.value), _.head)
    val viaFile = Calibre.parseSqliteJson(fixture("calibre_sqlite.json"), lib).fold(p => fail(p.value), _.head)
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
      .fold(p => fail(p.value), _.head)
      .copy(formats = List(CollectionEntry.Formats("/lib/x.epub"), CollectionEntry.Formats("/lib/x.pdf")))
    assertEquals(b.pick(None).map(_.value.takeRight(4)), Some(".pdf"))
    assertEquals(b.pick(Some(Calibre.Book.Format("epub"))).map(_.value.takeRight(5)), Some(".epub"))
    assertEquals(b.modify(_.formats).setTo(List(CollectionEntry.Formats("/lib/x.mobi"))).pick(None), None)

  // ── the metadata guard ──────────────────────────────────────────────────────────────────────
  test("the metadata guard refuses empty and Unknown title/author"):
    assert(Calibre.guard(com.histmersion.api.library.Title("A Real Title"), List(AuthorName("A Real Author"))).isRight)
    assert(Calibre.guard(com.histmersion.api.library.Title(""), List(AuthorName("Author"))).isLeft)
    assert(Calibre.guard(com.histmersion.api.library.Title("Untitled"), List(AuthorName("Author"))).isLeft)
    assert(Calibre.guard(com.histmersion.api.library.Title("Title"), Nil).isLeft)
    assert(Calibre.guard(com.histmersion.api.library.Title("Title"), List(AuthorName("Unknown Author"))).isLeft)
    val both = Calibre.guard(com.histmersion.api.library.Title("unknown"), List(AuthorName("  "))).left.map(_.value).getOrElse("")
    assert(both.contains("title") && both.contains("author"), both)

  test("blankness is case- and whitespace-insensitive"):
    assert(Calibre.isBlank("  UNKNOWN  "))
    assert(Calibre.isBlank("n/a"))
    assert(!Calibre.isBlank("Unknown Pleasures")) // a real title that merely starts with the word

  // ── command assembly (nothing is executed) ──────────────────────────────────────────────────
  test("`calibredb add` reports the new id"):
    assertEquals(Calibre.parseAddedId("Added book ids: 1693\n"), Some(Calibre.Book.Id(1693)))
    assertEquals(Calibre.parseAddedId("Added book ids: 1693,1694"), Some(Calibre.Book.Id(1693)))
    assertEquals(Calibre.parseAddedId("nothing happened"), None)

  test("SQL literals are escaped, so a quote in a search term cannot break the query"):
    assertEquals(Calibre.sqlLiteral("O'Brien"), "'O''Brien'")
    val q = Calibre.sqliteQuery(s"b.title LIKE ${Calibre.sqlLiteral("%O'Brien%")}", com.histmersion.api.Paging.Limit(5))
    assert(q.contains("'%O''Brien%'"), q)
    assert(q.contains("LIMIT 5"))
    assert(!q.contains("\n"))

  test("push refuses junk metadata and never reaches calibredb"):
    val shell = RecordingShell()
    val c = Calibre(cfg(), shell)
    val bad = c.add(Path.of("/tmp/whatever.pdf"), Calibre.Push(Calibre.Push.Title("Unknown"), List(Calibre.Push.Authors("Unknown Author"))))
    assert(bad.isLeft, bad)
    assertEquals(shell.commands, Nil, "the guard must fire before anything is executed")

  test("push refuses while the Calibre GUI holds the library"):
    val shell = RecordingShell(running = true)
    val r = Calibre(cfg(), shell).add(Path.of("/tmp/x.pdf"), Calibre.Push(Calibre.Push.Title("T"), List(Calibre.Push.Authors("A"))))
    assert(r.left.exists(_.value.contains("GUI is running")), r)

  /** A shell whose `calibredb list` answers with one book (the entry being updated). */
  private class LibraryShell(onShelf: String) extends RecordingShell():
    override def run(cmd: List[String]) =
      commands = commands :+ cmd
      if cmd.contains("list") then Right(onShelf) else Right("")

  private val meta = Calibre.Push(
    Calibre.Push.Title("Perspectives on Hittite Civilization"),
    List(AuthorName("Güterbock, Hans G."), AuthorName("Hoffner, Harry A.")),
    doi = Some(Doi("10.1/x")),
    year = Some(com.histmersion.api.catalogue.Year(1997)),
    publisher = Some(CollectionEntry.Publisher("Oriental Institute")),
    summary = Some(Source.Summary("our summary")),
    tags = List(CollectionEntry.Tags("hittite"))
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
    assertEquals(cmd.take(5), List("calibredb", "set_metadata", "--with-library", lib.value, "1567"))
    cmd.drop(5).grouped(2).map { case List("--field", f) => f; case o => fail(s"not a field: $o") }.toList

  test(
    "update writes title, First-Last authors, the record's year, merged identifiers and the topics as tags, and fills publisher and description where Calibre has none"
  ):
    val bare = LibraryShell(shelf("0101-01-01T00:00:00+00:00", "", "-"))
    val changed = Calibre(cfg(), bare).update(Calibre.Book.Id(1567), meta).map(_.map(_.field))
    assertEquals(
      changed,
      Right(
        List(
          Calibre.Field.Title,
          Calibre.Field.Authors,
          Calibre.Field.Pubdate,
          Calibre.Field.Identifiers,
          Calibre.Field.Tags,
          Calibre.Field.Publisher,
          Calibre.Field.Comments
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
    assertEquals(Calibre(cfg(), held).update(Calibre.Book.Id(1567), meta).map(_.size), Right(5))
    val fs = fields(held)
    assert(fs.contains("pubdate:1997-01-01"), fs)
    assert(!fs.exists(f => f.startsWith("publisher:") || f.startsWith("comments:")), fs)

  test("update MERGES identifiers: the record's DOI is set, Calibre's ISBN and every other scheme are kept"):
    val ids = """{"isbn":"9780918986672","google":"abcDEF","asin":"B000X","doi":"10.9/old"}"""
    val held = LibraryShell(shelf("1997-01-01T00:00:00+00:00", "Oriental Institute", "x", identifiers = ids))
    val _ = Calibre(cfg(), held).update(Calibre.Book.Id(1567), meta.modify(_.isbn).setTo(Some(Calibre.Push.Isbn("9999999999999"))))
    assert(fields(held).contains("identifiers:asin:B000X,doi:10.1/x,google:abcDEF,isbn:9780918986672"), fields(held))
    assertEquals(
      Calibre.mergeIdentifiers(
        Map(Calibre.Book.IdentifiersKey("google") -> Calibre.Book.IdentifiersValue("g")),
        Some(Doi("10.1/x")),
        Some(com.histmersion.api.library.CalibreGap.Isbn("978"))
      ),
      Map(
        Calibre.Book.IdentifiersKey("google") -> Calibre.Book.IdentifiersValue("g"),
        Calibre.Book.IdentifiersKey("doi") -> Calibre.Book.IdentifiersValue("10.1/x"),
        Calibre.Book.IdentifiersKey("isbn") -> Calibre.Book.IdentifiersValue("978")
      )
    )
    assertEquals(
      Calibre.mergeIdentifiers(Map.empty, Some(Doi("10.1/a,b")), None),
      Map.empty[Calibre.Book.IdentifiersKey, Calibre.Book.IdentifiersValue],
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
    assertEquals(Calibre(cfg(), same).update(Calibre.Book.Id(1567), meta), Right(Nil))
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
      Calibre(cfg(), same).update(
        Calibre.Book.Id(1567),
        meta.modify(_.title).setTo(Calibre.Push.Title(" Perspectives on Hittite Civilization"))
      ),
      Right(Nil)
    )
    assert(!same.commands.exists(_.contains("set_metadata")), same.commands)
    assertEquals(
      Calibre.mergeIdentifiers(
        Map(Calibre.Book.IdentifiersKey("doi") -> Calibre.Book.IdentifiersValue("10.1/ABC")),
        Some(Doi("10.1/abc")),
        None
      ),
      Map(Calibre.Book.IdentifiersKey("doi") -> Calibre.Book.IdentifiersValue("10.1/ABC"))
    )
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
    val _ = Calibre(cfg(), renamed).update(
      Calibre.Book.Id(1567),
      meta.modify(_.title).setTo(Calibre.Push.Title("  Perspectives on Hittite Civilization  "))
    )
    assertEquals(fields(renamed), List("title:Perspectives on Hittite Civilization"))

  test("authors: First Last, without role words, brackets or library life dates"):
    def n(a: AuthorName) = Calibre.normaliseAuthor(a)
    assertEquals(n(AuthorName("Güterbock, Hans G.")), AuthorName("Hans G. Güterbock"))
    assertEquals(n(AuthorName("Clunies Ross, Margaret (ed.)")), AuthorName("Margaret Clunies Ross"))
    assertEquals(n(AuthorName("Ken Dowden (ed.)")), AuthorName("Ken Dowden"))
    assertEquals(n(AuthorName("Gimbutienė, Marija (1921-1994)")), AuthorName("Marija Gimbutienė"))
    assertEquals(n(AuthorName("Gimbutas, Marija, 1921-1994")), AuthorName("Marija Gimbutas"))
    assertEquals(n(AuthorName("Nilsson, Martin P., b. 1874")), AuthorName("Martin P. Nilsson"))
    assertEquals(n(AuthorName("Dowden, Ken, eds.")), AuthorName("Ken Dowden"))
    assertEquals(n(AuthorName("Hans Güterbock, trans.")), AuthorName("Hans Güterbock"))
    assertEquals(n(AuthorName("West, M. L. ed.")), AuthorName("M. L. West"))
    assertEquals(n(AuthorName("King, Martin Luther, Jr.")), AuthorName("Martin Luther King Jr."))
    assertEquals(n(AuthorName("Smith, Ed")), AuthorName("Ed Smith"), "a man called Ed is not an editor")
    assertEquals(n(AuthorName("Walter Burkert")), AuthorName("Walter Burkert"))
    assertEquals(Calibre.normaliseAuthors(List(AuthorName("Burkert, Walter"), AuthorName("Walter Burkert (ed.)"), AuthorName(" "))), List(AuthorName("Walter Burkert")))

  // ── update --all (#831) ─────────────────────────────────────────────────────────────────────
  private def libraryOf(books: String*) = LibraryShell(books.mkString("[", ",", "]"))
  private def entry(id: Int, title: String, authors: String, pubdate: String) =
    s"""{"id":$id,"title":"$title","authors":"$authors","pubdate":"$pubdate","publisher":"P","comments":"c","tags":["t"],"identifiers":{},"formats":[]}"""
  private val linked = List(
    LinkedRecord(
      LinkedRecord.Citekey("burkert1985_greek"),
      LinkedRecord.CalibreId(10),
      Calibre.Push(Calibre.Push.Title("Greek Religion"), List(AuthorName("Burkert, Walter")), year = Some(com.histmersion.api.catalogue.Year(1985)), tags = List(CollectionEntry.Tags("t")))
    ),
    LinkedRecord(
      LinkedRecord.Citekey("page1959_history"),
      LinkedRecord.CalibreId(11),
      Calibre.Push(Calibre.Push.Title("History and the Homeric Iliad"), List(AuthorName("Page, Denys")), year = Some(com.histmersion.api.catalogue.Year(1959)), tags = List(CollectionEntry.Tags("t")))
    ),
    LinkedRecord(LinkedRecord.Citekey("guterbock1948_kumarbi"), LinkedRecord.CalibreId(12), Calibre.Push(Calibre.Push.Title("Kumarbi"), List(AuthorName("Güterbock, Hans G.")), tags = List(CollectionEntry.Tags("t")))),
    LinkedRecord(
      LinkedRecord.Citekey("guterbock1997_perspectives"),
      LinkedRecord.CalibreId(12),
      Calibre.Push(Calibre.Push.Title("Perspectives"), List(AuthorName("Güterbock, Hans G.")), tags = List(CollectionEntry.Tags("t")))
    ),
    LinkedRecord(LinkedRecord.Citekey("gone2000_x"), LinkedRecord.CalibreId(13), Calibre.Push(Calibre.Push.Title("Gone"), List(AuthorName("Someone"))))
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
    val out = Calibre(cfg(), shell).updateAll(linked, apply = CollectionSync.Request.Apply(false)).fold(p => fail(p.value), identity)
    assertEquals(
      out.map(e => e.calibreId -> e.outcome),
      List(
        Calibre.Book.Id(10) -> UpdateOutcome.UpToDate,
        Calibre.Book.Id(11) -> UpdateOutcome.Change(List(Calibre.Change(Calibre.Field.Pubdate, Calibre.Change.From("1963"), Calibre.Change.To("1959-01-01")))),
        Calibre.Book.Id(12) -> UpdateOutcome.Shared,
        Calibre.Book.Id(13) -> UpdateOutcome.Missing
      )
    )
    assertEquals(shell.commands.count(_.contains("list")), 1, "one listing of the library, not one per entry")
    assert(!shell.commands.exists(_.contains("set_metadata")), shell.commands)
    val md = Calibre.markdownUpdateAll(out, applied = com.histmersion.api.library.CollectionUpdateAll.Applied(false))
    assert(md.contains("| 11 | page1959_history | pubdate | 1963 | 1959-01-01 |"), md)
    assert(md.contains("guterbock1948_kumarbi, guterbock1997_perspectives") && md.contains("--apply"), md)

  test("update --all --apply writes only the entries that change, and refuses while the GUI runs"):
    val shell = libraryOf(shelfAll*)
    val out = Calibre(cfg(), shell).updateAll(linked, apply = CollectionSync.Request.Apply(true)).fold(p => fail(p.value), identity)
    assertEquals(
      out.find(_.calibreId == Calibre.Book.Id(11)).map(_.outcome),
      Some(UpdateOutcome.Applied(List(Calibre.Change(Calibre.Field.Pubdate, Calibre.Change.From("1963"), Calibre.Change.To("1959-01-01")))))
    )
    assertEquals(
      shell.commands.filter(_.contains("set_metadata")),
      List(List("calibredb", "set_metadata", "--with-library", lib.value, "11", "--field", "pubdate:1959-01-01"))
    )
    val gui = RecordingShell(running = true)
    assert(Calibre(cfg(), gui).updateAll(linked, apply = CollectionSync.Request.Apply(true)).left.exists(_.value.contains("GUI is running")))
    assertEquals(gui.commands, Nil)

  test("update of an id Calibre does not have is an error, and nothing is written"):
    val none = LibraryShell("[]")
    assert(Calibre(cfg(), none).update(Calibre.Book.Id(1567), meta).isLeft)
    assert(!none.commands.exists(_.contains("set_metadata")))

  test("set writes only the named, settable fields, and refuses junk before running anything"):
    val shell = LibraryShell(shelf("1986-06-01T00:00:00+00:00", "Gallimard", "blurb"))
    assertEquals(
      Calibre(cfg(), shell).set(
        Calibre.Book.Id(1567),
        List(
          CollectionFieldValue(CollectionFieldValue.Name("languages"), CollectionFieldValue.Value("fra")),
          CollectionFieldValue(CollectionFieldValue.Name("pubdate"), CollectionFieldValue.Value("1968-01-01"))
        )
      ),
      Right(())
    )
    assertEquals(fields(shell), List("languages:fra", "pubdate:1968-01-01"))
    val quiet = RecordingShell()
    assert(
      Calibre(cfg(), quiet)
        .set(Calibre.Book.Id(1), List(CollectionFieldValue(CollectionFieldValue.Name("formats"), CollectionFieldValue.Value("x"))))
        .left
        .exists(_.value.contains("cannot set formats"))
    )
    assert(
      Calibre(cfg(), quiet)
        .set(
          Calibre.Book.Id(1),
          List(CollectionFieldValue(CollectionFieldValue.Name("authors"), CollectionFieldValue.Value("Walter Burkert & Unknown")))
        )
        .isLeft
    )
    assert(Calibre(cfg(), quiet).set(Calibre.Book.Id(1), Nil).isLeft)
    assertEquals(quiet.commands, Nil)

  test("update refuses junk metadata and a running GUI before running anything"):
    assert(
      Calibre(cfg(), RecordingShell()).update(Calibre.Book.Id(1), Calibre.Push(Calibre.Push.Title("T"), List(Calibre.Push.Authors("(ed.)")))).isLeft,
      "a name that is nothing but a role is no author"
    )
    val shell = RecordingShell()
    assert(Calibre(cfg(), shell).update(Calibre.Book.Id(1), Calibre.Push(Calibre.Push.Title("Unknown"), List(Calibre.Push.Authors("A")))).isLeft)
    assertEquals(shell.commands, Nil)
    val gui = RecordingShell(running = true)
    assert(
      Calibre(cfg(), gui)
        .update(Calibre.Book.Id(1), Calibre.Push(Calibre.Push.Title("T"), List(Calibre.Push.Authors("A"))))
        .left
        .exists(_.value.contains("GUI is running"))
    )
    assertEquals(gui.commands, Nil)

  test("reading falls back to sqlite3 when calibredb refuses (the GUI case)"):
    val shell = new RecordingShell():
      override def run(cmd: List[String]) =
        commands = commands :+ cmd
        if cmd.head.endsWith("calibredb") then Left(Shell.Problem("Another calibre program is running"))
        else Right(scala.io.Source.fromResource("acquire/calibre_sqlite.json").mkString)
    val books = Calibre(cfg(), shell).search(Api.Search.Text("likelihood"))
    assertEquals(books.map(_.map(_.id)), Right(List(Calibre.Book.Id(1691))))
    assertEquals(shell.commands.size, 2, "calibredb first, then sqlite3")
    assert(shell.commands(1).contains("-readonly"), "the metadata.db must be opened READ-ONLY")

  // ── import: citekeys (#714) ─────────────────────────────────────────────────────────────────
  private def book(
      id: Calibre.Book.Id,
      title: Calibre.Book.Title,
      authors: List[Calibre.Book.Authors],
      pubdate: Calibre.Book.Pubdate
  ) =
    Calibre.Book(id, title, authors, Some(pubdate), None, Nil, None, Map.empty, Nil)

  test("two volumes of one work get two citekeys, with the whole surname"):
    val v1 = book(
      Calibre.Book.Id(1805),
      Calibre.Book.Title("The Pre-Christian Religions of the North: Research and Reception, Volume I: From the Middle Ages to c. 1830"),
      List(Calibre.Book.Authors("Clunies Ross, Margaret (ed.)")),
      Calibre.Book.Pubdate("2018-01-01T00:00:00+00:00")
    )
    val v2 = v1.modify(
      _.id).setTo(Calibre.Book.Id(1806)).modify(_.title).setTo(Calibre.Book.Title("The Pre-Christian Religions of the North: Research and Reception, Volume II: From c. 1830 to the Present")
    )
    assertEquals(Calibre.citekeyFor(v1), Citekey("cluniesross2018_christian_v1"))
    assertEquals(Calibre.citekeyFor(v2), Citekey("cluniesross2018_christian_v2"))

  test("volume numbers: arabic and roman, and words that merely start with 'vol' are not volumes"):
    assertEquals(Calibre.volumeOf(com.histmersion.api.library.Title("Prolonged Echoes: Volume 1, The Myths")), Some(Calibre.VolumeNumber(1)))
    assertEquals(Calibre.volumeOf(com.histmersion.api.library.Title("Collected Papers, Vol. IV")), Some(Calibre.VolumeNumber(4)))
    assertEquals(Calibre.volumeOf(com.histmersion.api.library.Title("vol 12")), Some(Calibre.VolumeNumber(12)))
    assertEquals(Calibre.volumeOf(com.histmersion.api.library.Title("Volcanic Winters and the Volvic spring")), None)
    assertEquals(Calibre.volumeOf(com.histmersion.api.library.Title("The Iliad")), None)

  test("import citekeys transliterate accented letters instead of dropping them (#830)"):
    assertEquals(
      Calibre.citekeyFor(
        book(
          Calibre.Book.Id(1),
          Calibre.Book.Title("Phoenicians and the Making of the Mediterranean"),
          List(Calibre.Book.Authors("López-Ruiz, Carolina")),
          Calibre.Book.Pubdate("2021-01-01T00:00:00+00:00")
        )
      ),
      Citekey("lopezruiz2021_phoenicians")
    )
    assertEquals(
      Calibre.citekeyFor(
        book(
          Calibre.Book.Id(2),
          Calibre.Book.Title("Ženské postavy"),
          List(Calibre.Book.Authors("Běťáková, Marie")),
          Calibre.Book.Pubdate("2012-01-01T00:00:00+00:00")
        )
      ),
      Citekey("betakova2012_zenske")
    )
    assertEquals(
      Calibre.citekeyFor(
        book(
          Calibre.Book.Id(3),
          Calibre.Book.Title("Études"),
          List(Calibre.Book.Authors("Øystein Ståhlberg")),
          Calibre.Book.Pubdate("1990-01-01T00:00:00+00:00")
        )
      ),
      Citekey("stahlberg1990_etudes")
    )
    assertEquals(
      Calibre.citekeyFor(
        book(
          Calibre.Book.Id(4),
          Calibre.Book.Title("Słowianie"),
          List(Calibre.Book.Authors("Łowmiański, Henryk")),
          Calibre.Book.Pubdate("1963-01-01T00:00:00+00:00")
        )
      ),
      Citekey("lowmianski1963_slowianie")
    )
    assertEquals(
      Calibre.citekeyFor(
        book(
          Calibre.Book.Id(5),
          Calibre.Book.Title("Straße"),
          List(Calibre.Book.Authors("Weiß, Ærø")),
          Calibre.Book.Pubdate("2001-01-01T00:00:00+00:00")
        )
      ),
      Citekey("weiss2001_strasse")
    )

  test("Calibre's 0101 'no date' is no year at all"):
    val b = book(
      Calibre.Book.Id(1),
      Calibre.Book.Title("Twilight of the Gods"),
      List(Calibre.Book.Authors("Ian Young")),
      Calibre.Book.Pubdate("0101-01-01T00:00:00+00:00")
    )
    assertEquals(b.year, None)
    assertEquals(Calibre.citekeyFor(b), Citekey("youngnd_twilight"))

  test("import refuses to merge a second Calibre entry into an existing record unless --citekey says so"):
    assertEquals(Calibre.importTarget(Citekey("k"), Calibre.Book.Id(1806), explicit = ImportTarget.Explicit(false), None), Right(ImportTarget.Create))
    assertEquals(
      Calibre.importTarget(Citekey("k"), Calibre.Book.Id(1806), explicit = ImportTarget.Explicit(false), Some(Some(Source.CalibreId("1806")))),
      Right(ImportTarget.Update(None))
    )
    val e = Calibre.importTarget(Citekey("k"), Calibre.Book.Id(1806), explicit = ImportTarget.Explicit(false), Some(Some(Source.CalibreId("1805")))).swap.toOption.get
    assert(e.value.contains("Calibre id 1805") && e.value.contains("--citekey"), e)
    assert(
      Calibre.importTarget(Citekey("k"), Calibre.Book.Id(1806), explicit = ImportTarget.Explicit(false), Some(None)).isLeft,
      "an unlinked record is not merged silently either"
    )
    // #721: an explicit --citekey re-links the record to the imported entry
    assertEquals(
      Calibre.importTarget(Citekey("k"), Calibre.Book.Id(1806), explicit = ImportTarget.Explicit(true), Some(Some(Source.CalibreId("1805")))),
      Right(ImportTarget.Update(Some(ImportTarget.Update.Previous("1805"))))
    )
    assertEquals(Calibre.importTarget(Citekey("k"), Calibre.Book.Id(1806), explicit = ImportTarget.Explicit(true), Some(None)), Right(ImportTarget.Update(None)))

  // ── push --replace (#652) ───────────────────────────────────────────────────────────────────
  private def tmpFile(ext: String) =
    val f = java.nio.file.Files.createTempFile("calibre-suite-", "." + ext)
    f.toFile.deleteOnExit(); f

  test("replace swaps the format on the linked entry with add_format — never a new entry"):
    val shell = LibraryShell(shelf("1986-06-01T00:00:00+00:00", "", ""))
    val f = tmpFile("pdf")
    assertEquals(Calibre(cfg(), shell).replaceFormat(Calibre.Book.Id(1567), f), Right(Calibre.Book.Format("pdf")))
    val cmd = shell.commands.find(_.contains("add_format")).getOrElse(fail("no add_format ran"))
    assertEquals(cmd, List("calibredb", "add_format", "--with-library", lib.value, "1567", f.toString))
    assert(!shell.commands.exists(c => c.contains("add") && !c.contains("add_format") && c(1) == "add"))

  test("replace refuses a running GUI, a missing entry, and a non-pdf/epub file before writing"):
    val f = tmpFile("pdf")
    val gui = RecordingShell(running = true)
    assert(Calibre(cfg(), gui).replaceFormat(Calibre.Book.Id(1), f).left.exists(_.value.contains("GUI is running")))
    assertEquals(gui.commands, Nil)
    val none = LibraryShell("[]")
    assert(Calibre(cfg(), none).replaceFormat(Calibre.Book.Id(1567), f).left.exists(_.value.contains("no Calibre entry 1567")))
    assert(!none.commands.exists(_.contains("add_format")))
    val quiet = RecordingShell()
    assert(Calibre(cfg(), quiet).replaceFormat(Calibre.Book.Id(1), tmpFile("mobi")).isLeft)
    assertEquals(quiet.commands, Nil)

  private def cfg() = CalibreCliConfig(lib, CalibreCliConfig.Calibredb("calibredb"), CalibreCliConfig.Sqlite("sqlite3"), CalibreCliConfig.OriginalsDir(""))

  /** A [[Shell]] that records instead of executing. */
  private class RecordingShell(running: Boolean = false) extends Shell:
    var commands: List[List[String]] = Nil // a test double is the one place a var is the simplest thing
    def run(cmd: List[String]): Either[Shell.Problem, String] =
      commands = commands :+ cmd
      Right("")
    def isRunning(processMatch: Shell.ProcessMatch): Boolean = running

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
        if cmd.head.endsWith("calibredb") then Left(Shell.Problem("Another calibre program is running")) else Right("")
    assertEquals(Calibre(cfg(), shell).search(Api.Search.Text("no such book")), Right(Nil))
    assertEquals(Calibre.markdownSearch(Nil), "_No matching books in the Calibre library._")

  test("unlinked: the entries no source links, newest (highest id) first, limited"):
    val books = List(
      book(Calibre.Book.Id(3), Calibre.Book.Title("Old"), List(Calibre.Book.Authors("A")), Calibre.Book.Pubdate("2001-01-01")),
      book(Calibre.Book.Id(9), Calibre.Book.Title("Newest"), List(Calibre.Book.Authors("B")), Calibre.Book.Pubdate("2020-01-01")),
      book(Calibre.Book.Id(5), Calibre.Book.Title("Linked"), List(Calibre.Book.Authors("C")), Calibre.Book.Pubdate("2010-01-01")),
      book(Calibre.Book.Id(7), Calibre.Book.Title("Middle"), List(Calibre.Book.Authors("D")), Calibre.Book.Pubdate("2015-01-01"))
    )
    assertEquals(Calibre.unlinked(books, Set(Calibre.Book.Id(5)), com.histmersion.api.Paging.Limit(10)).map(_.id), List(Calibre.Book.Id(9), Calibre.Book.Id(7), Calibre.Book.Id(3)))
    assertEquals(Calibre.unlinked(books, Set(Calibre.Book.Id(5)), com.histmersion.api.Paging.Limit(2)).map(_.id), List(Calibre.Book.Id(9), Calibre.Book.Id(7)))
    assertEquals(Calibre.unlinked(books, books.map(_.id).toSet, com.histmersion.api.Paging.Limit(10)), Nil)

  test("unlinked listing: title, authors, year and the file path of each entry"):
    val b =
      book(
        Calibre.Book.Id(9),
        Calibre.Book.Title("Newest"),
        List(Calibre.Book.Authors("Kristiansen, K.")),
        Calibre.Book.Pubdate("2020-01-01")
      ).modify(_.formats).setTo(List(CollectionEntry.Formats(s"$lib/K/Newest (9)/Newest.pdf")))
    val md = Calibre.markdownUnlinked(List(b), total = com.histmersion.api.Paging.Page.Total(3))
    assert(md.contains("1 of 3 Calibre entries not linked"), md)
    assert(md.contains("**9** · Newest (2020) — Kristiansen, K."), md)
    assert(md.contains(s"`$lib/K/Newest (9)/Newest.pdf`"), md)
    assert(md.contains("histmersion calibre import <id>"), md)
    assertEquals(Calibre.markdownUnlinked(Nil, com.histmersion.api.Paging.Page.Total(0)), "_Every Calibre entry is linked to a source._")

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
      .fold(p => fail(p.value), _.head)
    assertEquals(b.cover, Some(Calibre.Book.Cover("/lib/A/T (7)/cover.jpg")))
    val s = Calibre
      .parseSqliteJson("""[{"id":7,"title":"T","authors":"A","cover":"A/T (7)/cover.jpg"}]""", CalibreCliConfig.LibraryDir("/lib"))
      .fold(p => fail(p.value), _.head)
    assertEquals(s.cover, Some(Calibre.Book.Cover("/lib/A/T (7)/cover.jpg")))
    assertEquals(
      Calibre
        .parseSqliteJson("""[{"id":7,"title":"T","authors":"A","cover":null}]""", CalibreCliConfig.LibraryDir("/lib"))
        .fold(p => fail(p.value), _.head)
        .cover,
      None
    )
    assert(Calibre.sqliteQuery("1=1", com.histmersion.api.Paging.Limit(1)).contains("has_cover"))

  test("#1446: the Calibre-linked sources `media covers` reads — a citekey quoted, covered ones skipped"):
    val q = MediaCovers.linkedQuery(Some(Citekey("o'brien2001")), refresh = com.histmersion.api.media.MediaImportRequest.Refresh(false))
    assert(q.value.contains("s.citekey = 'o''brien2001'"), q)
    assert(q.value.contains("m.role = 'cover'") && q.value.contains("'^[0-9]+$'"), q)
    assert(!MediaCovers.linkedQuery(None, refresh = com.histmersion.api.media.MediaImportRequest.Refresh(true)).value.contains("NOT EXISTS"))

  test("an import into an existing record keeps a compiled summary (#896)"):
    assertEquals(Calibre.importSummary(Some(Source.Summary("Calibre blurb (Friedrich et al.")), Some(Source.Summary("Compiled summary."))), None)
    assertEquals(Calibre.importSummary(Some(Source.Summary("Calibre blurb")), None), Some(Source.Summary("Calibre blurb")))
    assertEquals(Calibre.importSummary(Some(Source.Summary("Calibre blurb")), Some(Source.Summary("  "))), Some(Source.Summary("Calibre blurb")))
    assertEquals(Calibre.importSummary(None, None), None)
