package histdb.cli

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

  private def cfg() = histdb.core.CalibreCliConfig(lib, "calibredb", "sqlite3", "")

  /** A [[Shell]] that records instead of executing. */
  private class RecordingShell(running: Boolean = false) extends Shell:
    var commands: List[List[String]] = Nil // a test double is the one place a var is the simplest thing
    def run(cmd: List[String]): Either[String, String] =
      commands = commands :+ cmd
      Right("")
    def isRunning(processMatch: String): Boolean = running
