package com.histmersion
package plugins.calibre
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.plugin.LocalFiles
import com.histmersion.pluginhost.DiskLocalFiles
import com.histmersion.plugin.Processes
import com.histmersion.pluginhost.SystemProcesses
import com.histmersion.lint.{LintRule, tolerated}

/** A Calibre library for the suites, built in a scratch directory: its books (`books.json`, which the fake `calibredb`
  * answers from and writes to), the books' files and covers, the `metadata.db` the `sqlite3` fallback reads (the same
  * books, in Calibre's schema), and the two stand-in programs with their argument logs. Nothing here ever touches the
  * user's own library: [[CalibreFixture.refuseRealLibrary]] fails every suite that would be pointed at it.
  */
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class CalibreFixture(dir: Path):
  private given LocalFiles = new DiskLocalFiles
  private given Processes = new SystemProcesses
  def calibredb: Path = dir.resolve("bin/calibredb")
  def sqlite3: Path = dir.resolve("bin/sqlite3")

  /** A copy of this library in `to` (a new directory), with its own logs and books: what a writing test runs on. */
  def copyTo(to: Path): CalibreFixture =
    CalibreFixture.refuseRealLibrary(to)
    Files.walk(dir).iterator.asScala.toList.foreach { p =>
      val q = to.resolve(dir.relativize(p).toString)
      if Files.isDirectory(p) then Files.createDirectories(q)
      else Files.copy(p, q, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES)
    }
    val books = to.resolve("books.json")
    Files.writeString(books, Files.readString(books, UTF_8).replace(dir.toString, to.toString), UTF_8)
    CalibreFixture(to)

  /** What the two programs were called with since the last call, oldest first. */
  def takeLog(): List[String] =
    List("calibredb.log", "sqlite3.log").flatMap { n =>
      val f = dir.resolve(n)
      if !Files.exists(f) then Nil
      else
        val lines = Files.readAllLines(f, UTF_8).asScala.toList
        Files.delete(f)
        lines
    }

  /** The fake `calibredb list` fails while this exists, as the real one does while the GUI holds the database. */
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  def lock(locked: Boolean): Unit =
    val f = dir.resolve("gui.lock")
    if locked then Files.writeString(f, "locked") else Files.deleteIfExists(f): Unit

object CalibreFixture:
  private given CanEqual[Path, Path] = CanEqual.derived

  /** One book of the fixture, in `calibredb list --for-machine`'s terms (`authors` joined by ` & `). */
  private given LocalFiles = new DiskLocalFiles
  private given Processes = new SystemProcesses
  @tolerated(LintRule.BooleanParam, "pending ADR 0027 (a state is an enum, not a Boolean)")
  final case class Book(
      id: Int,
      title: String,
      authors: List[String],
      pubdate: String,
      publisher: Option[String] = None,
      tags: List[String] = Nil,
      comments: Option[String] = None,
      identifiers: List[(String, String)] = Nil,
      formats: List[String] = Nil,
      cover: Boolean = false
  ):
    def path: String = s"Author $id/Book $id ($id)"

  val Books: List[Book] = List(
    Book(
      1,
      "Bronze Age tin and its trade",
      List("Sharon R. Stocker"),
      "2019-01-01T00:00:00+00:00",
      Some("Hesperia"),
      List("tin"),
      Some("<p>Tin in the Aegean.</p>"),
      List("doi" -> "10.1/zzcal.1"),
      List("pdf"),
      cover = true
    ),
    Book(
      2,
      "The Linear B tablets",
      List("Michael Ventris", "John Chadwick"),
      "1956-01-01T00:00:00+00:00",
      formats = List("pdf")
    ),
    Book(
      3,
      "A drifted title",
      List("Someone Else"),
      "1999-01-01T00:00:00+00:00",
      identifiers = List("google" -> "g3"),
      formats = List("epub")
    ),
    Book(4, "Unknown", List("Unknown"), "0101-01-01T00:00:00+00:00", formats = List("pdf")),
    Book(
      5,
      "Hittite Myths, Volume II",
      List("Harry A. Hoffner"),
      "1998-01-01T00:00:00+00:00",
      Some("Scholars Press"),
      List("hittite", "myth"),
      Some("<b>The myths</b> of Hatti."),
      List("isbn" -> "9780788504884"),
      List("pdf", "epub"),
      cover = true
    ),
    Book(6, "Bronze Age warfare", List("Robert Drews"), "1993-01-01T00:00:00+00:00", formats = List("epub")),
    Book(7, "A Kindle-only book", List("Kay Format"), "2020-01-01T00:00:00+00:00", formats = List("mobi")),
    Book(8, "Collision of keys", List("Jane Zzcal"), "2001-01-01T00:00:00+00:00", formats = List("pdf"))
  )

  /** Fail loudly when `library` is — after `~` and links are resolved — the user's own Calibre library: the default
    * `~/Calibre Library` or the one `HISTMERSION_CALIBRE_LIBRARY_DIR` names. Every suite that reads or writes a library
    * calls this first.
    */
  def refuseRealLibrary(library: Path): Unit =
    val home = Path.of(System.getProperty("user.home"))
    def real(p: Path): Path =
      val abs = (if p.toString.startsWith("~") then home.resolve(p.toString.drop(1).stripPrefix("/")) else p)
        .toAbsolutePath()
        .normalize()
      if Files.exists(abs) then abs.toRealPath() else abs
    val forbidden = (home.resolve("Calibre Library") :: Option(System.getenv("HISTMERSION_CALIBRE_LIBRARY_DIR"))
      .filter(_.nonEmpty)
      .map(Path.of(_))
      .toList).map(real)
    val target = real(library)
    if forbidden.exists(f => target == f || target.startsWith(f) || f.startsWith(target)) then
      throw IllegalStateException(
        s"refusing to use $library as a test library: it is (or holds) the user's own Calibre library"
      )

  /** A new fixture library in `dir`. */
  def create(dir: Path): CalibreFixture =
    refuseRealLibrary(dir)
    Files.createDirectories(dir)
    Files.writeString(dir.resolve("histmersion-fixture-library"), "a test library\n")
    val bin = Files.createDirectories(dir.resolve("bin"))
    List("calibredb" -> "fake-calibredb.py", "sqlite3" -> "fake-sqlite3.py").foreach { (name, res) =>
      val p = bin.resolve(name)
      Files.write(p, scala.io.Source.fromResource(s"calibre/$res")(using scala.io.Codec.UTF8).mkString.getBytes(UTF_8))
      Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rwxr-xr-x")): Unit
    }
    val json = Books.map { b =>
      val bookDir = Files.createDirectories(dir.resolve(b.path))
      val files = b.formats.map { ext =>
        val f = bookDir.resolve(s"Book ${b.id}.$ext")
        Files.write(
          f,
          (if ext == "pdf" then "%PDF-1.4\n" else "PK\u0003\u0004").getBytes(UTF_8) ++
            s"book ${b.id} ${"x" * 2048}".getBytes(UTF_8)
        )
        f.toString
      }
      val cover = Option.when(b.cover) {
        val c = bookDir.resolve("cover.jpg")
        Files.write(c, Array[Byte](-1, -40, -1, -32) ++ s"cover ${b.id}".getBytes(UTF_8))
        c.toString
      }
      Json.fromFields(
        List(
          "id" -> b.id.asJson,
          "title" -> b.title.asJson,
          "authors" -> b.authors.mkString(" & ").asJson,
          "pubdate" -> b.pubdate.asJson,
          "tags" -> b.tags.asJson,
          "identifiers" -> Json.fromFields(b.identifiers.map((k, v) => k -> v.asJson)),
          "formats" -> files.asJson
        ) ++ b.publisher.map("publisher" -> _.asJson) ++ b.comments.map("comments" -> _.asJson) ++
          cover.map("cover" -> _.asJson)
      )
    }
    Files.writeString(dir.resolve("books.json"), Json.arr(json*).spaces2, UTF_8)
    metadataDb(dir)
    CalibreFixture(dir)

  private def q(s: String): String = "'" + s.replace("'", "''") + "'"

  /** `metadata.db` in Calibre's schema (the tables the read-only fallback reads), built by the system's `sqlite3`. */
  private def metadataDb(dir: Path): Unit =
    val authors = Books.flatMap(_.authors).distinct.zipWithIndex.map((a, i) => a -> (i + 1)).toMap
    val publishers = Books.flatMap(_.publisher).distinct.zipWithIndex.map((p, i) => p -> (i + 1)).toMap
    val tags = Books.flatMap(_.tags).distinct.zipWithIndex.map((t, i) => t -> (i + 1)).toMap
    val ddl = List(
      "CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT, author_sort TEXT, pubdate TEXT, path TEXT, has_cover BOOL)",
      "CREATE TABLE authors (id INTEGER PRIMARY KEY, name TEXT)",
      "CREATE TABLE books_authors_link (id INTEGER PRIMARY KEY, book INTEGER, author INTEGER)",
      "CREATE TABLE publishers (id INTEGER PRIMARY KEY, name TEXT)",
      "CREATE TABLE books_publishers_link (id INTEGER PRIMARY KEY, book INTEGER, publisher INTEGER)",
      "CREATE TABLE tags (id INTEGER PRIMARY KEY, name TEXT)",
      "CREATE TABLE books_tags_link (id INTEGER PRIMARY KEY, book INTEGER, tag INTEGER)",
      "CREATE TABLE comments (id INTEGER PRIMARY KEY, book INTEGER, text TEXT)",
      "CREATE TABLE identifiers (id INTEGER PRIMARY KEY, book INTEGER, type TEXT, val TEXT)",
      "CREATE TABLE data (id INTEGER PRIMARY KEY, book INTEGER, format TEXT, name TEXT)"
    )
    val rows = authors.toList.sortBy(_._2).map((a, i) => s"INSERT INTO authors VALUES ($i, ${q(a)})") ++
      publishers.toList.sortBy(_._2).map((p, i) => s"INSERT INTO publishers VALUES ($i, ${q(p)})") ++
      tags.toList.sortBy(_._2).map((t, i) => s"INSERT INTO tags VALUES ($i, ${q(t)})") ++
      Books.flatMap { b =>
        List(
          s"INSERT INTO books VALUES (${b.id}, ${q(b.title)}, ${q(b.authors.mkString(" & "))}, ${q(b.pubdate)}, " +
            s"${q(b.path)}, ${if b.cover then 1 else 0})"
        ) ++ b.authors.map(a => s"INSERT INTO books_authors_link (book, author) VALUES (${b.id}, ${authors(a)})") ++
          b.publisher.map(p =>
            s"INSERT INTO books_publishers_link (book, publisher) VALUES (${b.id}, ${publishers(p)})"
          ) ++ b.tags.map(t => s"INSERT INTO books_tags_link (book, tag) VALUES (${b.id}, ${tags(t)})") ++
          b.comments.map(c => s"INSERT INTO comments (book, text) VALUES (${b.id}, ${q(c)})") ++
          b.identifiers.map((k, v) =>
            s"INSERT INTO identifiers (book, type, val) VALUES (${b.id}, ${q(k)}, ${q(v)})"
          ) ++
          b.formats.map(f =>
            s"INSERT INTO data (book, format, name) VALUES (${b.id}, ${q(f.toUpperCase)}, ${q(s"Book ${b.id}")})"
          )
      }
    val sql = Files.writeString(dir.resolve("metadata.sql"), (ddl ++ rows).map(_ + ";").mkString("\n") + "\n")
    val p = new ProcessBuilder("/usr/bin/sqlite3", dir.resolve("metadata.db").toString, s".read $sql")
      .redirectErrorStream(true)
      .start()
    val out = new String(p.getInputStream.readAllBytes(), UTF_8)
    if p.waitFor() != 0 then throw IllegalStateException(s"sqlite3 could not build metadata.db: $out")
    Files.delete(sql)
