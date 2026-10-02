package com.histmersion.cli

import com.histmersion.core.{Acquire, CalibreCliConfig}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** One book as Calibre holds it — the shape both readers below produce (`calibredb list
  * --for-machine` and the `sqlite3 -readonly` fallback normalise to this same record). */
final case class CalibreBook(
    id: Int,
    title: String,
    authors: List[String],
    pubdate: Option[String],
    publisher: Option[String],
    tags: List[String],
    comments: Option[String],
    identifiers: Map[String, String],
    formats: List[String]
):
  /** Calibre writes `0101-01-01` for "no date"; a year below [[Acquire.MinCitekeyYear]] is that
    * placeholder, not a publication year (#714). */
  def year: Option[Int] = pubdate.map(_.take(4)).filter(_.forall(_.isDigit)).flatMap(_.toIntOption)
    .filter(_ >= Acquire.MinCitekeyYear)
  def doi: Option[String]  = identifiers.get("doi")
  def isbn: Option[String] = identifiers.get("isbn")

  /** The file to import: the requested format, else PDF, else EPUB (both carry a text layer). */
  def pick(format: Option[String]): Option[String] =
    val wanted = format.map(_.toLowerCase).toList ++ List("pdf", "epub")
    wanted.iterator.map(ext => formats.find(_.toLowerCase.endsWith("." + ext))).collectFirst {
      case Some(f) => f
    }

/** Running an external program. Behind a trait so the parsers and the guards can be tested without
  * a Calibre installation. */
trait Shell:
  def run(cmd: List[String]): Either[String, String]
  def isRunning(processMatch: String): Boolean

object Shell:
  /** The real one: `java.lang.ProcessBuilder`, stdout captured, stderr folded into the error. */
  object System extends Shell:
    def run(cmd: List[String]): Either[String, String] =
      scala.util.Try {
        val p   = new ProcessBuilder(cmd.asJava).redirectErrorStream(false).start()
        val out = new String(p.getInputStream.readAllBytes, "UTF-8")
        val err = new String(p.getErrorStream.readAllBytes, "UTF-8")
        val rc  = p.waitFor()
        if rc == 0 then Right(out) else Left(s"${cmd.head} exited $rc: ${err.trim.take(400)}")
      }.fold(e => Left(s"${cmd.headOption.getOrElse("command")} could not be run: ${e.getMessage}"), identity)

    def isRunning(processMatch: String): Boolean =
      run(List("pgrep", "-f", processMatch)).exists(_.trim.nonEmpty)

/** Host-side Calibre integration — the ONE local integration the CLI is allowed, because the Calibre
  * library lives on the user's machine and the server (possibly a container) cannot see it.
  *
  * Reading happens two ways, in this order:
  *   1. `calibredb list --for-machine`, which is Calibre's own supported interface;
  *   2. `sqlite3 -readonly -json` straight against `metadata.db`, for when the Calibre GUI holds the
  *      database and `calibredb` refuses.
  *
  * Both are READ-ONLY. `metadata.db` is opened with `-readonly` precisely so a bug here can never
  * damage the user's library; the only writes this file performs are the explicit `calibredb add` /
  * `set_metadata` of a `push`, which additionally refuse to run while the GUI is open.
  */
final class Calibre(cfg: CalibreCliConfig, shell: Shell = Shell.System):

  private def libArgs = List("--with-library", cfg.library)

  /** The Calibre GUI holds an exclusive lock on the library for writes. */
  def guiRunning: Boolean = shell.isRunning("/Applications/calibre.app/Contents/MacOS/calibre")

  def libraryExists: Boolean = Files.isRegularFile(Path.of(cfg.library, "metadata.db"))

  // ── reading ─────────────────────────────────────────────────────────────────────────────────

  def search(query: String, limit: Int = 20): Either[String, List[CalibreBook]] =
    viaCalibredb(Some(query), limit).orElse(viaSqlite(Some(query), None, limit))

  /** The whole library (read-only), for the audit: one listing instead of a call per entry. */
  def all(limit: Int = 1000000): Either[String, List[CalibreBook]] =
    viaCalibredb(None, limit).orElse(viaSqlite(None, None, limit))

  def byId(id: Int): Either[String, Option[CalibreBook]] =
    viaCalibredb(Some(s"id:$id"), 1).map(_.find(_.id == id))
      .orElse(viaSqlite(None, Some(id), 1).map(_.headOption))

  private def viaCalibredb(query: Option[String], limit: Int): Either[String, List[CalibreBook]] =
    val cmd = List(cfg.calibredb, "list") ++ libArgs ++
      List("--for-machine", "--fields", "id,title,authors,pubdate,publisher,tags,comments,identifiers,formats",
        "--limit", limit.toString) ++ query.toList.flatMap(q => List("--search", q))
    shell.run(cmd).flatMap(Calibre.parseMachineJson)

  private def viaSqlite(query: Option[String], id: Option[Int], limit: Int)
      : Either[String, List[CalibreBook]] =
    val where = id match
      case Some(n) => s"b.id = ${n.toInt}"
      case None    =>
        val like = Calibre.sqlLiteral("%" + query.getOrElse("") + "%")
        s"(b.title LIKE $like OR b.author_sort LIKE $like)"
    val sql = Calibre.sqliteQuery(where, limit)
    shell.run(List(cfg.sqlite, "-readonly", "-json", Path.of(cfg.library, "metadata.db").toString, sql))
      .flatMap(Calibre.parseSqliteJson(_, cfg.library))

  // ── writing (push) ──────────────────────────────────────────────────────────────────────────

  /** `calibredb add` + `set_metadata` — the port of `to_calibre.py`. Returns the NEW Calibre id.
    *
    * The metadata guard is the point: Calibre entries with an empty or "Unknown" title/author are
    * unfindable junk, and the whole reason this pipeline pushes metadata at all is that Calibre's own
    * metadata sources key on ISBN and cannot identify a journal article. */
  def add(file: Path, meta: CalibrePush): Either[String, Int] =
    for
      _  <- Calibre.guard(meta.title, meta.authors)
      _  <- Either.cond(!guiRunning, (),
              "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)")
      _  <- Either.cond(Files.isRegularFile(file), (), s"no such file: $file")
      out <- shell.run(addCmd(file, meta))
      id  <- Calibre.parseAddedId(out).toRight(s"calibredb add did not report a new id: ${out.trim.take(200)}")
      _   <- setMetadata(id, meta)
    yield id

  /** Rewrite an EXISTING entry's descriptive metadata from the corpus record — for when a record's
    * title or authors change after the push (e.g. a chapter split out of a volume left the volume's
    * entry titled as the chapter). The file, identifiers and tags are left alone: a book's ISBN and
    * the user's own tags live only in Calibre. */
  def update(id: Int, meta: CalibrePush): Either[String, Unit] =
    for
      _ <- Calibre.guard(meta.title, meta.authors)
      _ <- Either.cond(!guiRunning, (),
             "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)")
      cur <- byId(id).flatMap(_.toRight(s"there is no Calibre entry $id"))
      _   <- shell.run(Calibre.updateCmd(cfg.calibredb, libArgs, meta, cur))
    yield ()

  /** Set named fields on an existing entry — the explicit, reviewed counterpart of [[update]]
    * (`enrich-calibre` applies approved fixes with it). Same guards as every other write. */
  def set(id: Int, fields: List[(String, String)]): Either[String, Unit] =
    for
      cmd <- Calibre.setCmd(cfg.calibredb, libArgs, id, fields)
      _   <- Either.cond(!guiRunning, (),
               "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)")
      _   <- byId(id).flatMap(_.toRight(s"there is no Calibre entry $id"))
      _   <- shell.run(cmd)
    yield ()

  /** Replace the file of an EXISTING entry (#652): `calibredb add_format`, which replaces the entry's
    * format of the same type. Unlike [[add]] it never creates an entry, so the link stays valid. */
  def replaceFormat(id: Int, file: Path): Either[String, String] =
    for
      fmt <- Calibre.formatOf(file)
      _   <- Either.cond(!guiRunning, (),
               "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)")
      _   <- Either.cond(Files.isRegularFile(file), (), s"no such file: $file")
      _   <- byId(id).flatMap(_.toRight(s"there is no Calibre entry $id"))
      _   <- shell.run(Calibre.replaceFormatCmd(cfg.calibredb, libArgs, id, file))
    yield fmt

  private def addCmd(file: Path, meta: CalibrePush): List[String] =
    List(cfg.calibredb, "add", file.toString) ++ libArgs ++
      List("--title", meta.title, "--authors", meta.authors.mkString(" & "), "--duplicates") ++
      meta.doi.toList.flatMap(d => List("--identifier", s"doi:$d")) ++
      meta.isbn.toList.flatMap(i => List("--isbn", i)) ++
      (if meta.tags.isEmpty then Nil else List("--tags", meta.tags.mkString(",")))

  /** The fields `calibredb add` cannot set. Best-effort: a failure here leaves a usable entry. */
  private def setMetadata(id: Int, meta: CalibrePush): Either[String, Unit] =
    val fields = List(
      meta.publisher.map("publisher:" + _),
      meta.year.map(y => s"pubdate:$y-01-01"),
      meta.summary.map("comments:" + _)
    ).flatten
    if fields.isEmpty then Right(())
    else
      val cmd = List(cfg.calibredb, "set_metadata") ++ libArgs ++ List(id.toString) ++
        fields.flatMap(f => List("--field", f))
      shell.run(cmd).map(_ => ()).left.map(e => s"added as $id but metadata was not set: $e")

/** Where `calibre import` writes, once the citekey is known (#714). */
enum ImportTarget:
  /** No record has this citekey yet. */
  case Create
  /** The record exists; enrich it and link it to the imported entry. `previous` is the Calibre id it
    * was linked to before, when that was a different one. */
  case Update(previous: Option[String])

/** What `calibre push` sends into Calibre for one source. */
final case class CalibrePush(
    title: String,
    authors: List[String],
    doi: Option[String] = None,
    isbn: Option[String] = None,
    year: Option[Int] = None,
    publisher: Option[String] = None,
    summary: Option[String] = None,
    tags: List[String] = Nil
)

/** The PURE half: parsing what the two readers print, the metadata guard, and SQL assembly. */
object Calibre:

  /** Values that mean "nobody filled this in". Pushing one of these into Calibre creates an entry
    * nobody will ever find again, so both `import` and `push` refuse. */
  private val Blank = Set("", "unknown", "unknown author", "untitled", "n/a", "?", "none")

  def isBlank(s: String): Boolean = Blank.contains(Option(s).getOrElse("").trim.toLowerCase)

  /** The metadata guard, shared by `import` (Calibre → corpus) and `push` (corpus → Calibre). */
  def guard(title: String, authors: List[String]): Either[String, Unit] =
    val missing = List(
      Option.when(isBlank(title))("title"),
      Option.when(authors.forall(isBlank))("author")
    ).flatten
    Either.cond(missing.isEmpty, (),
      s"empty or Unknown ${missing.mkString(" and ")} — fix the metadata first; " +
        "an untitled / Unknown-Author entry is unfindable and must not be created")

  // ── import: citekeys and where they land ─────────────────────────────────────────────────────

  private val Volume = """(?i)\b(?:volume\s*|vol\.\s*|vol\s+)([0-9]+|[ivxlc]+)\b""".r

  /** The volume a title names — `Volume I`, `Vol. 2`, `volume 3` — so two volumes of one work get
    * two citekeys. */
  def volumeOf(title: String): Option[Int] =
    Volume.findFirstMatchIn(title).flatMap(m => m.group(1).toIntOption.orElse(roman(m.group(1)))).filter(_ > 0)

  private def roman(s: String): Option[Int] =
    val v = Map('i' -> 1, 'v' -> 5, 'x' -> 10, 'l' -> 50, 'c' -> 100)
    val ds = s.toLowerCase.toList.map(v)
    Option.when(ds.nonEmpty)(ds.zipAll(ds.drop(1), 0, 0).map((a, b) => if a < b then -a else a).sum)

  /** The citekey `calibre import` generates: the corpus convention ([[Acquire.citekey]]) plus `_v<n>`
    * for a numbered volume. */
  def citekeyFor(b: CalibreBook): String =
    Acquire.citekey(b.authors, b.year, Some(b.title)) + volumeOf(b.title).fold("")(n => s"_v$n")

  /** Whether importing Calibre entry `id` under `ck` may go ahead (#714: two volumes once generated
    * the same citekey and were silently merged into one record).
    *
    * @param explicit the user passed `--citekey`, so landing on an existing record is deliberate
    * @param existing `None` when no record has `ck`; otherwise that record's Calibre id */
  def importTarget(ck: String, id: Int, explicit: Boolean, existing: Option[Option[String]])
      : Either[String, ImportTarget] =
    existing match
      case None                                     => Right(ImportTarget.Create)
      case Some(Some(linked)) if linked == id.toString => Right(ImportTarget.Update(None))
      case Some(linked) if explicit                 => Right(ImportTarget.Update(linked))
      case Some(linked) =>
        val what = linked.fold("a record that is not linked to Calibre")(l => s"the record of Calibre id $l")
        Left(s"the generated citekey '$ck' is already $what — importing Calibre id $id would merge two " +
          s"works into one record. Pass --citekey <another key> for a separate record, or --citekey $ck " +
          "to import into that record on purpose")

  /** The formats `calibre push --replace` puts into an entry: the two the corpus imports. */
  val ReplaceableFormats: List[String] = List("pdf", "epub")

  def formatOf(file: Path): Either[String, String] =
    val ext = file.getFileName.toString.reverse.takeWhile(_ != '.').reverse.toLowerCase
    Either.cond(ReplaceableFormats.contains(ext), ext,
      s"'${file.getFileName}' is not a ${ReplaceableFormats.mkString(" or ")} file")

  /** `calibredb add_format` replaces the entry's existing format of the same type (that is its
    * default; `--dont-replace` would be the opposite). */
  def replaceFormatCmd(calibredb: String, libArgs: List[String], id: Int, file: Path): List[String] =
    List(calibredb, "add_format") ++ libArgs ++ List(id.toString, file.toString)

  /** `calibredb add` prints `Added book ids: 1693`. */
  private val AddedIds = """Added book ids?:\s*([0-9,]+)""".r
  /** `calibredb set_metadata` for [[Calibre.update]]: title and authors always — that is what the
    * command is for. Publisher, year and description only where Calibre has NONE: Calibre's describe
    * the edition on the shelf (a 1986 printing of a 1968 work, a Polish translation), and a store's
    * description is the user's data, not ours to replace with a corpus summary. */
  def updateCmd(calibredb: String, libArgs: List[String], meta: CalibrePush, current: CalibreBook): List[String] =
    def empty(v: Option[String]) = v.forall(s => isBlank(s) || s.trim == "-")
    val fields = List(
      Some("title:" + meta.title),
      Some("authors:" + meta.authors.mkString(" & ")),
      meta.publisher.filter(_ => empty(current.publisher)).map("publisher:" + _),
      meta.year.filter(_ => CalibreAudit.year(current).isEmpty).map(y => s"pubdate:$y-01-01"),
      meta.summary.filter(_ => empty(current.comments)).map("comments:" + _)
    ).flatten
    List(calibredb, "set_metadata") ++ libArgs ++ List(current.id.toString) ++ fields.flatMap(f => List("--field", f))

  /** The fields `calibre set` may write. Formats, covers and the file itself are Calibre's business. */
  val SettableFields: List[String] =
    List("title", "authors", "publisher", "pubdate", "languages", "identifiers", "tags", "comments", "series")

  /** `calibredb set_metadata` for [[Calibre.set]], or why it is refused: an unknown field, nothing to
    * set, or a blank/"Unknown" title or author (the metadata guard). */
  def setCmd(calibredb: String, libArgs: List[String], id: Int, fields: List[(String, String)]): Either[String, List[String]] =
    val unknown = fields.map(_._1).filterNot(SettableFields.contains)
    def value(k: String) = fields.collectFirst { case (`k`, v) => v }
    for
      _ <- Either.cond(fields.nonEmpty, (), "nothing to set — pass --field <name>:<value>")
      _ <- Either.cond(unknown.isEmpty, (), s"cannot set ${unknown.mkString(", ")} — settable: ${SettableFields.mkString(", ")}")
      _ <- value("title").fold(Right(()))(t => Either.cond(!isBlank(t), (), "refusing a blank or Unknown title"))
      _ <- value("authors").fold(Right(()))(a =>
             Either.cond(a.split("&").map(_.trim).forall(x => !isBlank(x)), (), "refusing a blank or Unknown author"))
    yield List(calibredb, "set_metadata") ++ libArgs ++ List(id.toString) ++
      fields.flatMap((k, v) => List("--field", s"$k:$v"))

  def parseAddedId(out: String): Option[Int] =
    AddedIds.findFirstMatchIn(out).map(_.group(1)).flatMap(_.split(",").headOption).flatMap(_.trim.toIntOption)

  /** `calibredb list --for-machine`: authors as `A & B`, tags/formats as arrays, identifiers as an
    * object, formats as ABSOLUTE paths. */
  def parseMachineJson(out: String): Either[String, List[CalibreBook]] =
    io.circe.parser.parse(out).left.map(e => s"calibredb returned unparseable JSON: ${e.getMessage}")
      .map(_.asArray.toList.flatten.flatMap { j =>
        val c = j.hcursor
        c.downField("id").as[Int].toOption.map { id =>
          CalibreBook(
            id = id,
            title = c.downField("title").as[String].getOrElse(""),
            authors = splitAuthors(c.downField("authors").as[String].getOrElse("")),
            pubdate = c.downField("pubdate").as[String].toOption,
            publisher = c.downField("publisher").as[String].toOption.filter(_.nonEmpty),
            tags = c.downField("tags").as[List[String]].getOrElse(Nil),
            comments = c.downField("comments").as[String].toOption.filter(_.nonEmpty),
            identifiers = c.downField("identifiers").as[Map[String, String]].getOrElse(Map.empty),
            formats = c.downField("formats").as[List[String]].getOrElse(Nil))
        }
      })

  /** `sqlite3 -json`: every column is a scalar, the multi-valued ones joined by [[sqliteQuery]], and
    * `formats` relative to the library root. */
  def parseSqliteJson(out: String, libraryDir: String): Either[String, List[CalibreBook]] =
    io.circe.parser.parse(out).left.map(e => s"sqlite3 returned unparseable JSON: ${e.getMessage}")
      .map(_.asArray.toList.flatten.flatMap { j =>
        val c = j.hcursor
        def s(f: String) = c.downField(f).as[String].toOption.map(_.trim).filter(_.nonEmpty)
        c.downField("id").as[Int].toOption.map { id =>
          CalibreBook(
            id = id,
            title = s("title").getOrElse(""),
            authors = splitAuthors(s("authors").getOrElse("")),
            pubdate = s("pubdate"),
            publisher = s("publisher"),
            tags = s("tags").toList.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty),
            comments = s("comments"),
            identifiers = s("identifiers").toList.flatMap(_.split(",")).flatMap { p =>
              p.split(":", 2) match
                case Array(k, v) => Some(k.trim -> v.trim)
                case _           => None
            }.toMap,
            formats = s("formats").toList.flatMap(_.split("\\|")).map(_.trim).filter(_.nonEmpty)
              .map(f => Path.of(libraryDir, f).toString))
        }
      })

  private def splitAuthors(s: String): List[String] =
    s.split(" & ").map(_.trim).filter(_.nonEmpty).toList

  /** Single-quote a SQL literal for the `sqlite3` command line. The only user value that reaches SQL
    * is a search term, and it goes through here. */
  def sqlLiteral(s: String): String = "'" + s.replace("'", "''") + "'"

  /** The read-only projection that mirrors `calibredb --for-machine` (Calibre's schema: `books` plus
    * link tables for authors/tags/publishers/identifiers, and `data` for the files). */
  def sqliteQuery(where: String, limit: Int): String =
    s"""SELECT b.id AS id, b.title AS title,
       | (SELECT group_concat(a.name, ' & ') FROM authors a
       |    JOIN books_authors_link l ON l.author = a.id WHERE l.book = b.id) AS authors,
       | b.pubdate AS pubdate,
       | (SELECT p.name FROM publishers p
       |    JOIN books_publishers_link l ON l.publisher = p.id WHERE l.book = b.id) AS publisher,
       | (SELECT group_concat(t.name, ',') FROM tags t
       |    JOIN books_tags_link l ON l.tag = t.id WHERE l.book = b.id) AS tags,
       | (SELECT text FROM comments WHERE book = b.id) AS comments,
       | (SELECT group_concat(i.type || ':' || i.val, ',') FROM identifiers i WHERE i.book = b.id) AS identifiers,
       | (SELECT group_concat(b.path || '/' || d.name || '.' || lower(d.format), '|')
       |    FROM data d WHERE d.book = b.id) AS formats
       |FROM books b WHERE $where ORDER BY b.id DESC LIMIT ${limit.max(1)}""".stripMargin
      .replace("\n", " ")

  /** The host-side original of a source, when a directory of fetched files is configured:
    * `<originals>/<citekey>/paper.*`. Used by `calibre push` until the server serves stored
    * originals (stream 2.4). */
  def originalFor(originalsDir: Option[String], citekey: String): Option[Path] =
    originalsDir.map(d => Path.of(d, citekey)).filter(Files.isDirectory(_)).flatMap { dir =>
      val s = Files.list(dir)
      try
        s.iterator.asScala
          .filter(p => Set("pdf", "epub").contains(
            p.getFileName.toString.toLowerCase.split("\\.").lastOption.getOrElse("")))
          .toList.sortBy(_.toString).headOption
      finally s.close()
    }

  /** Render a Calibre search result as Markdown (the CLI's own output — the server never sees these). */
  def markdownSearch(books: List[CalibreBook]): String =
    if books.isEmpty then "_No matching books in the Calibre library._"
    else
      val rows = books.map { b =>
        val fmts = b.formats.flatMap(_.split("\\.").lastOption).map(_.toUpperCase).distinct.mkString(", ")
        // a multi-author paper in Calibre can carry 200 names; a table cell shows the first few
        val who = b.authors.take(3).mkString(", ") + (if b.authors.sizeIs > 3 then " et al." else "")
        s"| ${b.id} | ${b.title.replace("|", "\\|")} | ${who.replace("|", "\\|")} " +
          s"| ${b.year.map(_.toString).getOrElse("—")} | $fmts |"
      }
      (List(s"### ${books.size} book(s)", "", "| id | title | authors | year | formats |", "|---|---|---|---:|---|")
        ++ rows ++ List("", "_Import one with `histmersion calibre import <id> --citekey <k>`._")).mkString("\n")
