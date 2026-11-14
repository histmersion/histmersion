package com.histmersion
package plugins.calibre
import com.histmersion.util.problem.Text.sentences
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.testsupport.ComparedByValue.given

import java.nio.file.Path
import com.histmersion.plugin.LocalFiles
import com.histmersion.pluginhost.DiskLocalFiles
import com.histmersion.plugin.Processes
import com.histmersion.pluginhost.SystemProcesses
import com.histmersion.plugin.CollectionFile
import com.histmersion.plugin.CollectionQuery

/** The Calibre plugin's reading and writing: both readers produce the same book, the metadata guard refuses junk, and
  * the commands assembled for `calibredb` are the ones we mean to run.
  *
  * The fixtures are REAL output captured from `calibredb list --for-machine` and from `sqlite3 -readonly -json` against
  * the same book, with the home directory rewritten. No Calibre installation is needed to run this.
  */
class CalibreSuite extends munit.FunSuite:

  private given LocalFiles = new DiskLocalFiles
  private given Processes = new SystemProcesses
  private def fixture(name: String) = Processes.Result.Out(scala.io.Source.fromResource(s"acquire/$name").mkString)

  private val lib = Calibre.Library.Value("/Users/example/Calibre Library")

  // ── the two readers agree ───────────────────────────────────────────────────────────────────
  test("calibredb --for-machine JSON → CalibreBook"):
    val b =
      Calibre.parseMachineJson(fixture("calibredb_machine.json")).fold(e => fail(e.sentences.mkString("; ")), _.head)
    assertEquals(b.id, 1691)
    assertEquals(b.title.value.take(22), "Likelihood calculation")
    assertEquals(b.authors, List("Philipp Rönchen", "Tilo Wiklund", "Harald Hammarström"))
    assertEquals(b.year, Some(2024))
    assertEquals(b.publisher, Some("Language Dynamics and Change"))
    assertEquals(b.doi, Some("10.1163/22105832-bja10032"))
    assert(b.tags.exists(_.value == "phylogenetics"))
    assert(b.comments.exists(_.value.contains("likelihood algorithm")))
    assertEquals(b.formats.size, 1)
    assert(b.formats.head.value.startsWith(lib.value), b.formats.head)

  test("the sqlite3 fallback yields the SAME book (it is the path used while the GUI is open)"):
    val viaDb =
      Calibre.parseMachineJson(fixture("calibredb_machine.json")).fold(e => fail(e.sentences.mkString("; ")), _.head)
    val viaFile =
      Calibre.parseSqliteJson(fixture("calibre_sqlite.json"), lib).fold(e => fail(e.sentences.mkString("; ")), _.head)
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
    assert(Calibre.parseMachineJson(Processes.Result.Out("Another calibre program is running")).isLeft)
    // (empty output is NOT unparseable: it is how sqlite3 says "no rows", below)
    assert(Calibre.parseSqliteJson(Processes.Result.Out("Error: no such table: books"), lib).isLeft)

  test("format choice: requested, else pdf, else epub"):
    val b = Calibre
      .parseMachineJson(fixture("calibredb_machine.json"))
      .fold(e => fail(e.sentences.mkString("; ")), _.head)
      .copy(formats = List(Calibre.Book.Formats("/lib/x.epub"), Calibre.Book.Formats("/lib/x.pdf")))
    assertEquals(b.pick(None).map(_.value.takeRight(4)), Some(".pdf"))
    assertEquals(b.pick(Some(CollectionFile.Format("epub"))).map(_.value.takeRight(5)), Some(".epub"))
    assertEquals(b.copy(formats = List(Calibre.Book.Formats("/lib/x.mobi"))).pick(None), None)

  // ── the metadata guard ──────────────────────────────────────────────────────────────────────
  test("the metadata guard refuses empty and Unknown title/author"):
    assert(Calibre.guard(Calibre.Push.Title("A Real Title"), List(Calibre.Author("A Real Author"))).isRight)
    assert(Calibre.guard(Calibre.Push.Title(""), List(Calibre.Author("Author"))).isLeft)
    assert(Calibre.guard(Calibre.Push.Title("Untitled"), List(Calibre.Author("Author"))).isLeft)
    assert(Calibre.guard(Calibre.Push.Title("Title"), Nil).isLeft)
    assert(Calibre.guard(Calibre.Push.Title("Title"), List(Calibre.Author("Unknown Author"))).isLeft)
    val both =
      Calibre
        .guard(Calibre.Push.Title("unknown"), List(Calibre.Author("  ")))
        .left
        .toOption
    assertEquals(
      both.map(_.toVector),
      Some(
        Vector(
          Problem.Invalid.Missing.here(Problem.Field("title")),
          Problem.Invalid.Missing.here(Problem.Field("author"))
        )
      )
    )

  test("blankness is case- and whitespace-insensitive"):
    assert(Calibre.isBlank("  UNKNOWN  "))
    assert(Calibre.isBlank("n/a"))
    assert(!Calibre.isBlank("Unknown Pleasures")) // a real title that merely starts with the word

  // ── command assembly (nothing is executed) ──────────────────────────────────────────────────
  test("`calibredb add` reports the new id"):
    assertEquals(Calibre.parseAddedId(Processes.Result.Out("Added book ids: 1693\n")), Some(1693))
    assertEquals(Calibre.parseAddedId(Processes.Result.Out("Added book ids: 1693,1694")), Some(1693))
    assertEquals(Calibre.parseAddedId(Processes.Result.Out("nothing happened")), None)

  test("SQL literals are escaped, so a quote in a search term cannot break the query"):
    assertEquals(Calibre.sqlLiteral(CollectionQuery.Text("O'Brien")), "'O''Brien'")
    val q = Calibre.sqliteQuery(
      Calibre.SqlWhere(s"b.title LIKE ${Calibre.sqlLiteral(CollectionQuery.Text("%O'Brien%")).value}"),
      Calibre.SqliteQueryLimit(5)
    )
    assert(q.value.contains("'%O''Brien%'"), q)
    assert(q.value.contains("LIMIT 5"))
    assert(!q.value.contains("\n"))

  test("push refuses junk metadata and never reaches calibredb"):
    val shell = RecordingShell()
    val c = Calibre(cfg(), shell)
    val bad = c.add(
      Path.of("/tmp/whatever.pdf"),
      Calibre.Push(Calibre.Push.Title("Unknown"), List(Calibre.Author("Unknown Author")))
    )
    assert(bad.isLeft, bad)
    assertEquals(shell.commands, Nil, "the guard must fire before anything is executed")

  test("push refuses while the Calibre GUI holds the library"):
    val shell = RecordingShell(running = true)
    val r =
      Calibre(cfg(), shell).add(Path.of("/tmp/x.pdf"), Calibre.Push(Calibre.Push.Title("T"), List(Calibre.Author("A"))))
    assert(r.left.exists(_.sentences.mkString("; ").contains("the Calibre GUI is running — quit it first")), r)

  /** A shell whose `calibredb list` answers with one book (the entry being updated). */
  private class LibraryShell(onShelf: String) extends RecordingShell():
    override def run(cmd: List[Processes.RunArgv]): Either[Problem, Processes.Result.Out] =
      commands = commands :+ cmd
      if cmd.exists(_.value == "list") then Right(Processes.Result.Out(onShelf)) else Right(Processes.Result.Out(""))

  private val meta = Calibre.Push(
    Calibre.Push.Title("Perspectives on Hittite Civilization"),
    List(Calibre.Author("Güterbock, Hans G."), Calibre.Author("Hoffner, Harry A.")),
    doi = Some(Calibre.Push.Doi("10.1/x")),
    year = Some(Calibre.Book.Year(1997)),
    publisher = Some(Calibre.Push.Publisher("Oriental Institute")),
    summary = Some(Calibre.Push.Summary("our summary")),
    tags = List(Calibre.Push.Tags("hittite"))
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
    val cmd = shell.commands.find(_.exists(_.value == "set_metadata")).getOrElse(fail("no set_metadata ran"))
    assertEquals(cmd.take(5), List("calibredb", "set_metadata", "--with-library", lib.value, "1567"))
    cmd.drop(5).map(_.value).grouped(2).map { case List("--field", f) => f; case o => fail(s"not a field: $o") }.toList

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

  test("update writes the record's year over the edition's, but never its publisher or a store description"):
    val held = LibraryShell(shelf("1986-06-01T00:00:00+00:00", "Gallimard", "The publisher's own blurb"))
    assertEquals(Calibre(cfg(), held).update(Calibre.Book.Id(1567), meta).map(_.size), Right(5))
    val fs = fields(held)
    assert(fs.contains("pubdate:1997-01-01"), fs)
    assert(!fs.exists(f => f.startsWith("publisher:") || f.startsWith("comments:")), fs)

  test("update MERGES identifiers: the record's DOI is set, Calibre's ISBN and every other scheme are kept"):
    val ids = """{"isbn":"9780918986672","google":"abcDEF","asin":"B000X","doi":"10.9/old"}"""
    val held = LibraryShell(shelf("1997-01-01T00:00:00+00:00", "Oriental Institute", "x", identifiers = ids))
    val _ =
      Calibre(cfg(), held).update(Calibre.Book.Id(1567), meta.copy(isbn = Some(Calibre.Push.Isbn("9999999999999"))))
    assert(fields(held).contains("identifiers:asin:B000X,doi:10.1/x,google:abcDEF,isbn:9780918986672"), fields(held))
    assertEquals(
      Calibre.mergeIdentifiers(
        Map(Calibre.Book.IdentifiersKey("google") -> Calibre.Book.IdentifiersValue("g")),
        Some(Calibre.Push.Doi("10.1/x")),
        Some(Calibre.Push.Isbn("978"))
      ),
      Map("google" -> "g", "doi" -> "10.1/x", "isbn" -> "978")
    )
    assertEquals(
      Calibre.mergeIdentifiers(Map.empty, Some(Calibre.Push.Doi("10.1/a,b")), None),
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
    assertEquals(Calibre(cfg(), same).update(Calibre.Book.Id(1567), meta), Right(Nil))
    assert(!same.commands.exists(_.exists(_.value == "set_metadata")), same.commands)

  // a title differing only by surrounding spaces, or a DOI only by case, is not a change
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
      Calibre(cfg(), same)
        .update(Calibre.Book.Id(1567), meta.copy(title = Calibre.Push.Title(" Perspectives on Hittite Civilization"))),
      Right(Nil)
    )
    assert(!same.commands.exists(_.exists(_.value == "set_metadata")), same.commands)
    assertEquals(
      Calibre.mergeIdentifiers(
        Map(Calibre.Book.IdentifiersKey("doi") -> Calibre.Book.IdentifiersValue("10.1/ABC")),
        Some(Calibre.Push.Doi("10.1/abc")),
        None
      ),
      Map("doi" -> "10.1/ABC")
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
    val _ = Calibre(cfg(), renamed)
      .update(Calibre.Book.Id(1567), meta.copy(title = Calibre.Push.Title("  Perspectives on Hittite Civilization  ")))
    assertEquals(fields(renamed), List("title:Perspectives on Hittite Civilization"))

  test("authors: First Last, without role words, brackets or library life dates"):
    def n(a: String) = Calibre.normaliseAuthor(Calibre.Author(a))
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
    assertEquals(
      Calibre.normaliseAuthors(
        List(Calibre.Author("Burkert, Walter"), Calibre.Author("Walter Burkert (ed.)"), Calibre.Author(" "))
      ),
      List("Walter Burkert")
    )

  // ── update --all ─────────────────────────────────────────────────────────────────────
  private def libraryOf(books: String*) = LibraryShell(books.mkString("[", ",", "]"))
  private def entry(id: Int, title: String, authors: String, pubdate: String) =
    s"""{"id":$id,"title":"$title","authors":"$authors","pubdate":"$pubdate","publisher":"P","comments":"c","tags":["t"],"identifiers":{},"formats":[]}"""
  private val linked = List(
    LinkedRecord(
      LinkedRecord.Citekey("burkert1985_greek"),
      LinkedRecord.CalibreId(10),
      Calibre.Push(
        Calibre.Push.Title("Greek Religion"),
        List(Calibre.Author("Burkert, Walter")),
        year = Some(Calibre.Book.Year(1985)),
        tags = List(Calibre.Push.Tags("t"))
      )
    ),
    LinkedRecord(
      LinkedRecord.Citekey("page1959_history"),
      LinkedRecord.CalibreId(11),
      Calibre.Push(
        Calibre.Push.Title("History and the Homeric Iliad"),
        List(Calibre.Author("Page, Denys")),
        year = Some(Calibre.Book.Year(1959)),
        tags = List(Calibre.Push.Tags("t"))
      )
    ),
    LinkedRecord(
      LinkedRecord.Citekey("guterbock1948_kumarbi"),
      LinkedRecord.CalibreId(12),
      Calibre.Push(
        Calibre.Push.Title("Kumarbi"),
        List(Calibre.Author("Güterbock, Hans G.")),
        tags = List(Calibre.Push.Tags("t"))
      )
    ),
    LinkedRecord(
      LinkedRecord.Citekey("guterbock1997_perspectives"),
      LinkedRecord.CalibreId(12),
      Calibre.Push(
        Calibre.Push.Title("Perspectives"),
        List(Calibre.Author("Güterbock, Hans G.")),
        tags = List(Calibre.Push.Tags("t"))
      )
    ),
    LinkedRecord(
      LinkedRecord.Citekey("gone2000_x"),
      LinkedRecord.CalibreId(13),
      Calibre.Push(Calibre.Push.Title("Gone"), List(Calibre.Author("Someone")))
    )
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
    val out =
      Calibre(cfg(), shell)
        .updateAll(linked, apply = Calibre.UpdateAllApply(false))
        .fold(e => fail(e.sentences.mkString("; ")), identity)
    assertEquals(
      out.map(e => e.calibreId -> e.outcome),
      List(
        10 -> UpdateOutcome.UpToDate,
        11 -> UpdateOutcome.Change(
          List(Calibre.Change(Calibre.Field.Pubdate, Calibre.Change.From("1963"), Calibre.Change.To("1959-01-01")))
        ),
        12 -> UpdateOutcome.Shared,
        13 -> UpdateOutcome.Missing
      )
    )
    assertEquals(shell.commands.count(_.exists(_.value == "list")), 1, "one listing of the library, not one per entry")
    assert(!shell.commands.exists(_.exists(_.value == "set_metadata")), shell.commands)

  test("update --all --apply writes only the entries that change, and refuses while the GUI runs"):
    val shell = libraryOf(shelfAll*)
    val out =
      Calibre(cfg(), shell)
        .updateAll(linked, apply = Calibre.UpdateAllApply(true))
        .fold(e => fail(e.sentences.mkString("; ")), identity)
    assertEquals(
      out.find(_.calibreId.value == 11).map(_.outcome),
      Some(
        UpdateOutcome.Applied(
          List(Calibre.Change(Calibre.Field.Pubdate, Calibre.Change.From("1963"), Calibre.Change.To("1959-01-01")))
        )
      )
    )
    assertEquals(
      shell.commands.filter(_.exists(_.value == "set_metadata")),
      List(List("calibredb", "set_metadata", "--with-library", lib.value, "11", "--field", "pubdate:1959-01-01"))
    )
    val gui = RecordingShell(running = true)
    assert(
      Calibre(cfg(), gui)
        .updateAll(linked, apply = Calibre.UpdateAllApply(true))
        .left
        .exists(_.sentences.mkString("; ").contains("the Calibre GUI is running — quit it first"))
    )
    assertEquals(gui.commands, Nil)

  test("update of an id Calibre does not have is an error, and nothing is written"):
    val none = LibraryShell("[]")
    assert(Calibre(cfg(), none).update(Calibre.Book.Id(1567), meta).isLeft)
    assert(!none.commands.exists(_.exists(_.value == "set_metadata")))

  test("set writes only the named, settable fields, and refuses junk before running anything"):
    val shell = LibraryShell(shelf("1986-06-01T00:00:00+00:00", "Gallimard", "blurb"))
    assertEquals(
      Calibre(cfg(), shell).set(
        Calibre.Book.Id(1567),
        List(
          Calibre.Field.Name("languages") -> Calibre.Field.Value("fra"),
          Calibre.Field.Name("pubdate") -> Calibre.Field.Value("1968-01-01")
        )
      ),
      Right(())
    )
    assertEquals(fields(shell), List("languages:fra", "pubdate:1968-01-01"))
    val quiet = RecordingShell()
    assert(
      Calibre(cfg(), quiet)
        .set(Calibre.Book.Id(1), List(Calibre.Field.Name("formats") -> Calibre.Field.Value("x")))
        .left
        .exists(_.sentences.mkString("; ").contains("unknown field name 'formats'"))
    )
    assert(
      Calibre(cfg(), quiet)
        .set(Calibre.Book.Id(1), List(Calibre.Field.Name("authors") -> Calibre.Field.Value("Walter Burkert & Unknown")))
        .isLeft
    )
    assert(Calibre(cfg(), quiet).set(Calibre.Book.Id(1), Nil).isLeft)
    assertEquals(quiet.commands, Nil)

  test("update refuses junk metadata and a running GUI before running anything"):
    assert(
      Calibre(cfg(), RecordingShell())
        .update(Calibre.Book.Id(1), Calibre.Push(Calibre.Push.Title("T"), List(Calibre.Author("(ed.)"))))
        .isLeft,
      "a name that is nothing but a role is no author"
    )
    val shell = RecordingShell()
    assert(
      Calibre(cfg(), shell)
        .update(Calibre.Book.Id(1), Calibre.Push(Calibre.Push.Title("Unknown"), List(Calibre.Author("A"))))
        .isLeft
    )
    assertEquals(shell.commands, Nil)
    val gui = RecordingShell(running = true)
    assert(
      Calibre(cfg(), gui)
        .update(Calibre.Book.Id(1), Calibre.Push(Calibre.Push.Title("T"), List(Calibre.Author("A"))))
        .left
        .exists(_.sentences.mkString("; ").contains("the Calibre GUI is running — quit it first"))
    )
    assertEquals(gui.commands, Nil)

  test("reading falls back to sqlite3 when calibredb refuses (the GUI case)"):
    val shell = new RecordingShell():
      override def run(cmd: List[Processes.RunArgv]): Either[Problem, Processes.Result.Out] =
        commands = commands :+ cmd
        if cmd.head.value.endsWith("calibredb") then
          Left(
            Problem.Io.Process(
              Problem.Io.Tool("calibredb"),
              Problem.Io.Ending.Exited(Problem.Io.ExitCode(1), External.Stderr("Another calibre program is running"))
            )
          )
        else Right(fixture("calibre_sqlite.json"))
    val books = Calibre(cfg(), shell).search(CollectionQuery.Text("likelihood"))
    assertEquals(books.map(_.map(_.id)), Right(List(1691)))
    assertEquals(shell.commands.size, 2, "calibredb first, then sqlite3")
    assert(shell.commands(1).exists(_.value == "-readonly"), "the metadata.db must be opened READ-ONLY")

  private def book(
      id: Calibre.Book.Id,
      title: Calibre.Book.Title,
      authors: List[Calibre.Author],
      pubdate: Calibre.Book.Pubdate
  ) =
    Calibre.Book(id, title, authors, Some(pubdate), None, Nil, None, Map.empty, Nil)

  test("Calibre's 0101 'no date' is no year at all"):
    val b = book(
      Calibre.Book.Id(1),
      Calibre.Book.Title("Twilight of the Gods"),
      List(Calibre.Author("Ian Young")),
      Calibre.Book.Pubdate("0101-01-01T00:00:00+00:00")
    )
    assertEquals(b.year, None)

  private def tmpFile(ext: String) =
    val f = java.nio.file.Files.createTempFile("calibre-suite-", "." + ext)
    f.toFile.deleteOnExit(); f

  test("replace swaps the format on the linked entry with add_format — never a new entry"):
    val shell = LibraryShell(shelf("1986-06-01T00:00:00+00:00", "", ""))
    val f = tmpFile("pdf")
    assertEquals(Calibre(cfg(), shell).replaceFormat(Calibre.Book.Id(1567), f), Right("pdf"))
    val cmd = shell.commands.find(_.exists(_.value == "add_format")).getOrElse(fail("no add_format ran"))
    assertEquals(cmd, List("calibredb", "add_format", "--with-library", lib.value, "1567", f.toString))
    assert(
      !shell.commands.exists(c =>
        c.exists(_.value == "add") && !c.exists(_.value == "add_format") && c(1).value == "add"
      )
    )

  test("replace refuses a running GUI, a missing entry, and a non-pdf/epub file before writing"):
    val f = tmpFile("pdf")
    val gui = RecordingShell(running = true)
    assert(
      Calibre(cfg(), gui)
        .replaceFormat(Calibre.Book.Id(1), f)
        .left
        .exists(_.sentences.mkString("; ").contains("the Calibre GUI is running — quit it first"))
    )
    assertEquals(gui.commands, Nil)
    val none = LibraryShell("[]")
    assert(
      Calibre(cfg(), none)
        .replaceFormat(Calibre.Book.Id(1567), f)
        .left
        .exists(_.sentences.mkString("; ").contains("no book id '1567'"))
    )
    assert(!none.commands.exists(_.exists(_.value == "add_format")))
    val quiet = RecordingShell()
    assert(Calibre(cfg(), quiet).replaceFormat(Calibre.Book.Id(1), tmpFile("mobi")).isLeft)
    assertEquals(quiet.commands, Nil)

  private def cfg() = Calibre.Library(
    lib,
    Calibre.Settings.ExecutablePath("calibredb"),
    Calibre.Settings.Sqlite("sqlite3"),
    Shell.ProcessMatch("/Applications/calibre.app/Contents/MacOS/calibre")
  )

  /** A [[Shell]] that records instead of executing. */
  private class RecordingShell(running: Boolean = false) extends Shell:
    var commands: List[List[Processes.RunArgv]] = Nil // a test double is the one place a var is the simplest thing
    def run(cmd: List[Processes.RunArgv]): Either[Problem, Processes.Result.Out] =
      commands = commands :+ cmd
      Right(Processes.Result.Out(""))
    def isRunning(processMatch: Shell.ProcessMatch): Boolean = running

  // ── search: no matches, and the unlinked entries ─────────────────────────────────────
  test("a search with no match is an empty list, not 'unparseable JSON' — on both readers"):
    assertEquals(Calibre.parseSqliteJson(Processes.Result.Out(""), lib), Right(Nil))
    assertEquals(Calibre.parseSqliteJson(Processes.Result.Out("  \n"), lib), Right(Nil))
    assertEquals(Calibre.parseMachineJson(Processes.Result.Out("")), Right(Nil))
    assertEquals(Calibre.parseMachineJson(Processes.Result.Out("[]")), Right(Nil))
    // the GUI case: calibredb refuses, sqlite3 -json prints nothing at all for an empty result
    val shell = new RecordingShell():
      override def run(cmd: List[Processes.RunArgv]): Either[Problem, Processes.Result.Out] =
        commands = commands :+ cmd
        if cmd.head.value.endsWith("calibredb") then
          Left(
            Problem.Io.Process(
              Problem.Io.Tool("calibredb"),
              Problem.Io.Ending.Exited(Problem.Io.ExitCode(1), External.Stderr("Another calibre program is running"))
            )
          )
        else Right(Processes.Result.Out(""))
    assertEquals(Calibre(cfg(), shell).search(CollectionQuery.Text("no such book")), Right(Nil))

  test("a book's cover file — calibredb's `cover` field, the sqlite fallback's `has_cover`"):
    val b = Calibre
      .parseMachineJson(
        Processes.Result.Out("""[{"id":7,"title":"T","authors":"A","cover":"/lib/A/T (7)/cover.jpg"}]""")
      )
      .fold(e => fail(e.sentences.mkString("; ")), _.head)
    assertEquals(b.cover, Some("/lib/A/T (7)/cover.jpg"))
    val s = Calibre
      .parseSqliteJson(
        Processes.Result.Out("""[{"id":7,"title":"T","authors":"A","cover":"A/T (7)/cover.jpg"}]"""),
        Calibre.Library.Value("/lib")
      )
      .fold(e => fail(e.sentences.mkString("; ")), _.head)
    assertEquals(s.cover, Some("/lib/A/T (7)/cover.jpg"))
    assertEquals(
      Calibre
        .parseSqliteJson(
          Processes.Result.Out("""[{"id":7,"title":"T","authors":"A","cover":null}]"""),
          Calibre.Library.Value("/lib")
        )
        .fold(e => fail(e.sentences.mkString("; ")), _.head)
        .cover,
      None
    )
    assert(Calibre.sqliteQuery(Calibre.SqlWhere("1=1"), Calibre.SqliteQueryLimit(1)).value.contains("has_cover"))
