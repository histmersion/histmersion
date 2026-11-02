package com.histmersion.plugins.calibre

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import java.text.Normalizer

/** The Calibre library read and written through Calibre's own tools.
  *
  * Reading happens two ways, in this order:
  *   1. `calibredb list --for-machine`, which is Calibre's own supported interface;
  *   2. `sqlite3 -readonly -json` straight against `metadata.db`, for when the Calibre GUI holds the database and
  *      `calibredb` refuses.
  *
  * Both are READ-ONLY. `metadata.db` is opened with `-readonly` precisely so a bug here can never damage the user's
  * library; the only writes are the explicit `calibredb add` / `set_metadata` / `add_format` of a push, an update or a
  * set, which additionally refuse to run while the GUI is open.
  */
final class Calibre(cfg: com.histmersion.plugins.calibre.Calibre.Library, shell: Shell):

  private def libArgs = List("--with-library", cfg.library)

  /** The Calibre GUI holds an exclusive lock on the library for writes. */
  def guiRunning: Boolean = shell.isRunning(cfg.guiProcess)

  def libraryExists: Boolean = Files.isRegularFile(Path.of(cfg.library, "metadata.db"))

  // ── reading ─────────────────────────────────────────────────────────────────────────────────

  def search(query: String, limit: Int = 20): Either[String, List[com.histmersion.plugins.calibre.Calibre.Book]] =
    viaCalibredb(Some(query), limit).orElse(viaSqlite(Some(query), None, limit))

  /** The whole library (read-only), for the audit: one listing instead of a call per entry. */
  def all(limit: Int = 1000000): Either[String, List[com.histmersion.plugins.calibre.Calibre.Book]] =
    viaCalibredb(None, limit).orElse(viaSqlite(None, None, limit))

  def byId(id: Int): Either[String, Option[com.histmersion.plugins.calibre.Calibre.Book]] =
    viaCalibredb(Some(s"id:$id"), 1)
      .map(_.find(_.id == id))
      .orElse(viaSqlite(None, Some(id), 1).map(_.headOption))

  private def viaCalibredb(
      query: Option[String],
      limit: Int
  ): Either[String, List[com.histmersion.plugins.calibre.Calibre.Book]] =
    val cmd = List(cfg.calibredb, "list") ++ libArgs ++
      List(
        "--for-machine",
        "--fields",
        "id,title,authors,pubdate,publisher,tags,comments,identifiers,formats,cover",
        "--limit",
        limit.toString
      ) ++ query.toList.flatMap(q => List("--search", q))
    shell.run(cmd).flatMap(Calibre.parseMachineJson)

  private def viaSqlite(
      query: Option[String],
      id: Option[Int],
      limit: Int
  ): Either[String, List[com.histmersion.plugins.calibre.Calibre.Book]] =
    val where = id match
      case Some(n) => s"b.id = ${n.toInt}"
      case None    =>
        val like = Calibre.sqlLiteral("%" + query.getOrElse("") + "%")
        s"(b.title LIKE $like OR b.author_sort LIKE $like)"
    val sql = Calibre.sqliteQuery(where, limit)
    shell
      .run(List(cfg.sqlite, "-readonly", "-json", Path.of(cfg.library, "metadata.db").toString, sql))
      .flatMap(Calibre.parseSqliteJson(_, cfg.library))

  // ── writing (push) ──────────────────────────────────────────────────────────────────────────

  /** `calibredb add` + `set_metadata` — the port of `to_calibre.py`. Returns the NEW Calibre id.
    *
    * The metadata guard is the point: Calibre entries with an empty or "Unknown" title/author are unfindable junk, and
    * the whole reason this pipeline pushes metadata at all is that Calibre's own metadata sources key on ISBN and
    * cannot identify a journal article.
    */
  def add(file: Path, meta: com.histmersion.plugins.calibre.Calibre.Push): Either[String, Int] =
    for
      _ <- Calibre.guard(meta.title, meta.authors)
      _ <- Either.cond(
        !guiRunning,
        (),
        "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)"
      )
      _ <- Either.cond(Files.isRegularFile(file), (), s"no such file: $file")
      out <- shell.run(addCmd(file, meta))
      id <- Calibre.parseAddedId(out).toRight(s"calibredb add did not report a new id: ${out.trim.take(200)}")
      _ <- setMetadata(id, meta)
    yield id

  /** Bring an EXISTING entry in line with the corpus record — for when a record's title or authors change after the
    * push (a chapter split out of a volume left the volume's entry titled as the chapter), and to leave the entry
    * complete: identifiers merged, tags = the record's topics, the record's year. What is written is
    * [[Calibre.updatePlan]]; nothing is run when the entry already agrees. Returns what changed.
    */
  def update(
      id: Int,
      meta: com.histmersion.plugins.calibre.Calibre.Push
  ): Either[String, List[com.histmersion.plugins.calibre.Calibre.Change]] =
    for
      _ <- Calibre.guard(meta.title, Calibre.normaliseAuthors(meta.authors))
      _ <- Either.cond(!guiRunning, (), GuiRunning)
      cur <- byId(id).flatMap(_.toRight(s"there is no Calibre entry $id"))
      changes <- Calibre.updatePlan(meta, cur)
      _ <- write(id, changes)
    yield changes

  /** `calibre update --all`: plan the update of every linked entry against ONE listing of the library; with `apply`,
    * write the entries that would change. An entry linked from more than one record (a chapter and its volume) is left
    * alone — which record it should show is a decision for a person, `calibre update <citekey>`.
    */
  def updateAll(linked: List[LinkedRecord], apply: Boolean): Either[String, List[UpdateEntry]] =
    for
      _ <- Either.cond(!apply || !guiRunning, (), GuiRunning)
      books <- all()
    yield Calibre.planAll(linked, books.map(b => b.id -> b).toMap).map { e =>
      e.outcome match
        case UpdateOutcome.Change(cs) if apply =>
          e.copy(outcome = write(e.calibreId, cs).fold(UpdateOutcome.Failed(cs, _), _ => UpdateOutcome.Applied(cs)))
        case _ => e
    }

  /** One `set_metadata` with the planned fields; nothing at all when there are none. */
  private def write(id: Int, changes: List[com.histmersion.plugins.calibre.Calibre.Change]): Either[String, Unit] =
    if changes.isEmpty then Right(())
    else shell.run(Calibre.setMetadataCmd(cfg.calibredb, libArgs, id, changes)).map(_ => ())

  private val GuiRunning =
    "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)"

  /** Set named fields on an existing entry — the explicit, reviewed counterpart of [[update]] (`enrich-calibre` applies
    * approved fixes with it). Same guards as every other write.
    */
  def set(id: Int, fields: List[(String, String)]): Either[String, Unit] =
    for
      cmd <- Calibre.setCmd(cfg.calibredb, libArgs, id, fields)
      _ <- Either.cond(
        !guiRunning,
        (),
        "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)"
      )
      _ <- byId(id).flatMap(_.toRight(s"there is no Calibre entry $id"))
      _ <- shell.run(cmd)
    yield ()

  /** Replace the file of an EXISTING entry: `calibredb add_format`, which replaces the entry's format of the same type.
    * Unlike [[add]] it never creates an entry, so the link stays valid.
    */
  def replaceFormat(id: Int, file: Path): Either[String, String] =
    for
      fmt <- Calibre.formatOf(file)
      _ <- Either.cond(
        !guiRunning,
        (),
        "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)"
      )
      _ <- Either.cond(Files.isRegularFile(file), (), s"no such file: $file")
      _ <- byId(id).flatMap(_.toRight(s"there is no Calibre entry $id"))
      _ <- shell.run(Calibre.replaceFormatCmd(cfg.calibredb, libArgs, id, file))
    yield fmt

  private def addCmd(file: Path, meta: com.histmersion.plugins.calibre.Calibre.Push): List[String] =
    List(cfg.calibredb, "add", file.toString) ++ libArgs ++
      List("--title", meta.title.trim, "--authors", meta.authors.mkString(" & "), "--duplicates") ++
      meta.doi.toList.flatMap(d => List("--identifier", s"doi:$d")) ++
      meta.isbn.toList.flatMap(i => List("--isbn", i)) ++
      (if meta.tags.isEmpty then Nil else List("--tags", meta.tags.mkString(",")))

  /** The fields `calibredb add` cannot set. Best-effort: a failure here leaves a usable entry. */
  private def setMetadata(id: Int, meta: com.histmersion.plugins.calibre.Calibre.Push): Either[String, Unit] =
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

/** The PURE half: parsing what the two readers print, the metadata guard, and SQL assembly. */

/** The pure half: parsing what the two readers print, the metadata guard, the update plan and the commands. */
object Calibre:

  /** Values that mean "nobody filled this in". Pushing one of these into Calibre creates an entry nobody will ever find
    * again, so both `import` and `push` refuse.
    */
  private val Blank = Set("", "unknown", "unknown author", "untitled", "n/a", "?", "none")

  def isBlank(s: String): Boolean = Blank.contains(Option(s).getOrElse("").trim.toLowerCase)

  /** The metadata guard, shared by `import` (Calibre → corpus) and `push` (corpus → Calibre). */
  def guard(title: String, authors: List[String]): Either[String, Unit] =
    val missing = List(
      Option.when(isBlank(title))("title"),
      Option.when(authors.forall(isBlank))("author")
    ).flatten
    Either.cond(
      missing.isEmpty,
      (),
      s"empty or Unknown ${missing.mkString(" and ")} — fix the metadata first; " +
        "an untitled / Unknown-Author entry is unfindable and must not be created"
    )

  // ── import: citekeys and where they land ─────────────────────────────────────────────────────

  /** The formats `calibre push --replace` puts into an entry: the two the corpus imports. */
  val ReplaceableFormats: List[String] = List("pdf", "epub")

  def formatOf(file: Path): Either[String, String] =
    val ext = file.getFileName.toString.reverse.takeWhile(_ != '.').reverse.toLowerCase
    Either.cond(
      ReplaceableFormats.contains(ext),
      ext,
      s"'${file.getFileName}' is not a ${ReplaceableFormats.mkString(" or ")} file"
    )

  /** `calibredb add_format` replaces the entry's existing format of the same type (that is its default;
    * `--dont-replace` would be the opposite).
    */
  def replaceFormatCmd(calibredb: String, libArgs: List[String], id: Int, file: Path): List[String] =
    List(calibredb, "add_format") ++ libArgs ++ List(id.toString, file.toString)

  /** `calibredb add` prints `Added book ids: 1693`. */
  private val AddedIds = """Added book ids?:\s*([0-9,]+)""".r

  /** What [[Calibre.update]] writes into `current` from the record, field by field, and only where it differs:
    *   - title, and the authors as "First Last" with no role or life dates ([[normaliseAuthor]]);
    *   - the record's YEAR, always (the user's decision of 2026-10-03: Calibre shows the year the corpus cites, not the
    *     printing on the shelf);
    *   - identifiers MERGED: the record's DOI is set, its ISBN added only when Calibre has none (an ISBN names the
    *     edition on the shelf), and every other scheme (google, asin, …) is kept;
    *   - tags = the record's topics, when it has any;
    *   - publisher and description only where Calibre has NONE: Calibre's describe the edition on the shelf, and a
    *     store's description is the user's data, not ours to replace.
    * Languages are not written: the corpus records no language for a source. `Left` when the record would put a blank
    * or Unknown title/author into Calibre.
    */
  def updatePlan(
      meta: com.histmersion.plugins.calibre.Calibre.Push,
      current: com.histmersion.plugins.calibre.Calibre.Book
  ): Either[String, List[com.histmersion.plugins.calibre.Calibre.Change]] =
    val authors = normaliseAuthors(meta.authors)
    guard(meta.title, authors).map { _ =>
      def empty(v: Option[String]) = v.forall(s => isBlank(s) || s.trim == "-")
      def change(f: com.histmersion.plugins.calibre.Calibre.Field, from: String, to: String) =
        Option.when(from != to)(com.histmersion.plugins.calibre.Calibre.Change(f, from, to))
      val ids = mergeIdentifiers(current.identifiers, meta.doi, meta.isbn)
      List(
        // compared and written trimmed: a stray space is not a different title
        Option.when(current.title.trim != meta.title.trim)(
          com.histmersion.plugins.calibre.Calibre
            .Change(com.histmersion.plugins.calibre.Calibre.Field.Title, current.title, meta.title.trim)
        ),
        change(
          com.histmersion.plugins.calibre.Calibre.Field.Authors,
          current.authors.mkString(" & "),
          authors.mkString(" & ")
        ),
        meta.year.flatMap(y =>
          Option.when(com.histmersion.plugins.calibre.Calibre.Audit.year(current) != Some(y))(
            com.histmersion.plugins.calibre.Calibre.Change(
              com.histmersion.plugins.calibre.Calibre.Field.Pubdate,
              com.histmersion.plugins.calibre.Calibre.Audit.year(current).fold("")(_.toString),
              s"$y-01-01"
            )
          )
        ),
        Option.when(ids != current.identifiers)(
          com.histmersion.plugins.calibre.Calibre.Change(
            com.histmersion.plugins.calibre.Calibre.Field.Identifiers,
            identifiersValue(current.identifiers),
            identifiersValue(ids)
          )
        ),
        Option.when(meta.tags.nonEmpty && meta.tags.distinct.sorted != current.tags.distinct.sorted)(
          com.histmersion.plugins.calibre.Calibre.Change(
            com.histmersion.plugins.calibre.Calibre.Field.Tags,
            current.tags.mkString(","),
            meta.tags.distinct.mkString(",")
          )
        ),
        meta.publisher
          .filter(p => empty(current.publisher) && !isBlank(p))
          .map(
            com.histmersion.plugins.calibre.Calibre
              .Change(com.histmersion.plugins.calibre.Calibre.Field.Publisher, current.publisher.getOrElse(""), _)
          ),
        meta.summary
          .filter(d => empty(current.comments) && !isBlank(d))
          .map(
            com.histmersion.plugins.calibre.Calibre
              .Change(com.histmersion.plugins.calibre.Calibre.Field.Comments, current.comments.getOrElse(""), _)
          )
      ).flatten
    }

  /** `calibredb set_metadata` writing `changes` into entry `id`. */
  def setMetadataCmd(
      calibredb: String,
      libArgs: List[String],
      id: Int,
      changes: List[com.histmersion.plugins.calibre.Calibre.Change]
  ): List[String] =
    List(calibredb, "set_metadata") ++ libArgs ++ List(id.toString) ++
      changes.flatMap(c => List("--field", s"${c.field.name}:${c.to}"))

  /** Calibre's identifiers with the record's merged in: the DOI is the record's (it names the work the corpus cites),
    * an ISBN only fills a gap, and nothing Calibre has is ever dropped. A DOI is case-insensitive, so one differing
    * only in case is left as Calibre has it. A value with a comma cannot cross `--field identifiers:` (the comma
    * separates the pairs), so it is left out.
    */
  def mergeIdentifiers(current: Map[String, String], doi: Option[String], isbn: Option[String]): Map[String, String] =
    def ok(v: String) = v.trim.nonEmpty && !v.contains(",")
    val sameDoi = (d: String) => current.get("doi").exists(_.trim.equalsIgnoreCase(d))
    current ++ doi.map(_.trim).filter(ok).filterNot(sameDoi).map("doi" -> _) ++
      isbn.map(_.trim).filter(ok).filterNot(_ => current.get("isbn").exists(_.trim.nonEmpty)).map("isbn" -> _)

  /** `type:value,type:value`, as `--field identifiers:` takes it, in a stable order. */
  def identifiersValue(ids: Map[String, String]): String =
    ids.toList.sortBy(_._1).map((k, v) => s"$k:$v").mkString(",")

  /** Role words a library or a publisher appends to a name: `eds.`, `trans.`, `Hrsg.`. The short ones need their dot —
    * `Smith, Ed` is a man called Ed.
    */
  private val Role =
    """(?i)[,;]?\s*\b(?:ed\.|eds\.?|editors?|edited\s+by|trans\.|transl\.|translators?|translated\s+by|tr\.|comp\.|compilers?|hrsg\.?|hg\.|red\.)\s*$""".r

  /** A life span or a library's date qualifier at the end of a name: `, 1921-1994`, `, b. 1950`. */
  private val LifeDates =
    """(?i)[,\s]+(?:(?:b|d|fl|ca|c)\.\s*)?\d{3,4}\??(?:\s*[-–]\s*(?:\d{3,4}\??)?)?\.?\s*$""".r
  private val Suffix = Set("jr", "jr.", "sr", "sr.", "ii", "iii", "iv")

  /** One author as Calibre should show it: "First Last", without a bracketed role or life span, a trailing role word or
    * library-style dates. `Clunies Ross, Margaret (ed.)` → `Margaret Clunies Ross`; `King, Martin Luther, Jr.` →
    * `Martin Luther King Jr.`. A name with more commas than that is not one "Last, First" and is left in its order.
    */
  def normaliseAuthor(raw: String): String =
    val unbracketed = raw.replaceAll("\\([^)]*\\)|\\[[^\\]]*\\]", " ")
    val bare = Iterator
      .iterate(unbracketed.trim)(s =>
        LifeDates.replaceFirstIn(Role.replaceFirstIn(s, ""), "").trim.stripSuffix(",").stripSuffix(";").trim
      )
      .sliding(2)
      .collectFirst { case Seq(a, b) if a == b => a }
      .getOrElse(unbracketed.trim)
    val parts = bare.split(",").map(_.trim).filter(_.nonEmpty).toList
    val ordered = parts match
      case List(last, first)                                 => s"$first $last"
      case List(last, first, sfx) if Suffix(sfx.toLowerCase) => s"$first $last $sfx"
      case _                                                 => bare
    ordered.replaceAll("\\s+", " ").trim

  /** [[normaliseAuthor]] over a list, dropping names that end up empty and repeats. */
  def normaliseAuthors(authors: List[String]): List[String] =
    authors.map(normaliseAuthor).filter(_.nonEmpty).distinct

  /** Plan `update --all`: one [[UpdateEntry]] per linked Calibre id, in id order. */
  def planAll(
      linked: List[LinkedRecord],
      books: Map[Int, com.histmersion.plugins.calibre.Calibre.Book]
  ): List[UpdateEntry] =
    linked.groupBy(_.calibreId).toList.sortBy(_._1).map { (id, rs) =>
      val keys = rs.map(_.citekey).distinct.sorted
      val outcome =
        if keys.sizeIs > 1 then UpdateOutcome.Shared
        else
          books.get(id) match
            case None    => UpdateOutcome.Missing
            case Some(b) =>
              updatePlan(rs.head.meta, b).fold(
                UpdateOutcome.Refused(_),
                cs => if cs.isEmpty then UpdateOutcome.UpToDate else UpdateOutcome.Change(cs)
              )
      UpdateEntry(id, keys, outcome)
    }

  /** The fields `calibre set` may write. Formats, covers and the file itself are Calibre's business. */
  val SettableFields: List[String] =
    List("title", "authors", "publisher", "pubdate", "languages", "identifiers", "tags", "comments", "series")

  /** `calibredb set_metadata` for [[Calibre.set]], or why it is refused: an unknown field, nothing to set, or a
    * blank/"Unknown" title or author (the metadata guard).
    */
  def setCmd(
      calibredb: String,
      libArgs: List[String],
      id: Int,
      fields: List[(String, String)]
  ): Either[String, List[String]] =
    val unknown = fields.map(_._1).filterNot(SettableFields.contains)
    def value(k: String) = fields.collectFirst { case (`k`, v) => v }
    for
      _ <- Either.cond(fields.nonEmpty, (), "nothing to set — pass --field <name>:<value>")
      _ <- Either.cond(
        unknown.isEmpty,
        (),
        s"cannot set ${unknown.mkString(", ")} — settable: ${SettableFields.mkString(", ")}"
      )
      _ <- value("title").fold(Right(()))(t => Either.cond(!isBlank(t), (), "refusing a blank or Unknown title"))
      _ <- value("authors").fold(Right(()))(a =>
        Either.cond(a.split("&").map(_.trim).forall(x => !isBlank(x)), (), "refusing a blank or Unknown author")
      )
    yield List(calibredb, "set_metadata") ++ libArgs ++ List(id.toString) ++
      fields.flatMap((k, v) => List("--field", s"$k:$v"))

  def parseAddedId(out: String): Option[Int] =
    AddedIds.findFirstMatchIn(out).map(_.group(1)).flatMap(_.split(",").headOption).flatMap(_.trim.toIntOption)

  /** `calibredb list --for-machine`: authors as `A & B`, tags/formats as arrays, identifiers as an object, formats as
    * ABSOLUTE paths.
    */
  def parseMachineJson(out: String): Either[String, List[com.histmersion.plugins.calibre.Calibre.Book]] =
    if out.trim.isEmpty then Right(Nil) // no match: nothing is printed at all
    else
      io.circe.parser
        .parse(out)
        .left
        .map(e => s"calibredb returned unparseable JSON: ${e.getMessage}")
        .map(_.asArray.toList.flatten.flatMap { j =>
          val c = j.hcursor
          c.downField("id").as[Int].toOption.map { id =>
            com.histmersion.plugins.calibre.Calibre.Book(
              id = id,
              title = c.downField("title").as[String].getOrElse(""),
              authors = splitAuthors(c.downField("authors").as[String].getOrElse("")),
              pubdate = c.downField("pubdate").as[String].toOption,
              publisher = c.downField("publisher").as[String].toOption.filter(_.nonEmpty),
              tags = c.downField("tags").as[List[String]].getOrElse(Nil),
              comments = c.downField("comments").as[String].toOption.filter(_.nonEmpty),
              identifiers = c.downField("identifiers").as[Map[String, String]].getOrElse(Map.empty),
              formats = c.downField("formats").as[List[String]].getOrElse(Nil),
              cover = c.downField("cover").as[String].toOption.map(_.trim).filter(_.nonEmpty)
            )
          }
        })

  /** `sqlite3 -json`: every column is a scalar, the multi-valued ones joined by [[sqliteQuery]], and `formats` relative
    * to the library root.
    */
  def parseSqliteJson(
      out: String,
      libraryDir: String
  ): Either[String, List[com.histmersion.plugins.calibre.Calibre.Book]] =
    // `sqlite3 -json` prints NOTHING for an empty result, not `[]` — that was "exhausted input"
    if out.trim.isEmpty then Right(Nil)
    else
      io.circe.parser
        .parse(out)
        .left
        .map(e => s"sqlite3 returned unparseable JSON: ${e.getMessage}")
        .map(_.asArray.toList.flatten.flatMap { j =>
          val c = j.hcursor
          def s(f: String) = c.downField(f).as[String].toOption.map(_.trim).filter(_.nonEmpty)
          c.downField("id").as[Int].toOption.map { id =>
            com.histmersion.plugins.calibre.Calibre.Book(
              id = id,
              title = s("title").getOrElse(""),
              authors = splitAuthors(s("authors").getOrElse("")),
              pubdate = s("pubdate"),
              publisher = s("publisher"),
              tags = s("tags").toList.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty),
              comments = s("comments"),
              identifiers = s("identifiers").toList
                .flatMap(_.split(","))
                .flatMap { p =>
                  p.split(":", 2) match
                    case Array(k, v) => Some(k.trim -> v.trim)
                    case _           => None
                }
                .toMap,
              formats = s("formats").toList
                .flatMap(_.split("\\|"))
                .map(_.trim)
                .filter(_.nonEmpty)
                .map(f => Path.of(libraryDir, f).toString),
              cover = s("cover").map(f => Path.of(libraryDir, f).toString)
            )
          }
        })

  private def splitAuthors(s: String): List[String] =
    s.split(" & ").map(_.trim).filter(_.nonEmpty).toList

  /** Single-quote a SQL literal for the `sqlite3` command line. The only user value that reaches SQL is a search term,
    * and it goes through here.
    */
  def sqlLiteral(s: String): String = "'" + s.replace("'", "''") + "'"

  /** The read-only projection that mirrors `calibredb --for-machine` (Calibre's schema: `books` plus link tables for
    * authors/tags/publishers/identifiers, and `data` for the files).
    */
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
       |    FROM data d WHERE d.book = b.id) AS formats,
       | CASE WHEN b.has_cover THEN b.path || '/cover.jpg' END AS cover
       |FROM books b WHERE $where ORDER BY b.id DESC LIMIT ${limit.max(1)}""".stripMargin
      .replace("\n", " ")

  /** The PURE half of `histmersion calibre audit`: compare every linked entry with its record. */
  object Audit:

    /** Lower-case ASCII words: accents, punctuation and spacing are not disagreements. */
    def norm(s: String): String =
      Normalizer
        .normalize(s, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(java.util.Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim

    /** Equal after [[norm]], or one extends the other: Calibre often keeps or drops a subtitle. */
    def titleMatches(a: String, b: String): Boolean =
      val (x, y) = (norm(a), norm(b))
      x.nonEmpty && y.nonEmpty && (x.startsWith(y) || y.startsWith(x))

    /** "Güterbock, Hans G." and "Hans G. Güterbock" are the same surname; a bracketed role or life span ("Ken Dowden
      * (ed.)", "Gimbutienė, Marija (1921-1994)") is not part of the name.
      */
    def surname(author: String): String =
      val name = author.replaceAll("\\([^)]*\\)", " ").trim
      norm(if name.contains(",") then name.takeWhile(_ != ',') else name.split("\\s+").last)

    def authorsMatch(a: List[String], b: List[String]): Boolean =
      (a.map(surname).toSet & b.map(surname).toSet).exists(_.nonEmpty)

    /** Calibre's placeholder year for "unknown" is 101; anything before 1000 is not a real pubdate. */
    def year(b: Book): Option[Int] =
      b.pubdate.flatMap(_.take(4).toIntOption).filter(_ >= 1000)

    /** The metadata a scrape or a bad import leaves: blank/Unknown, or a title that is a web page. */
    def junk(b: Book): Boolean =
      Calibre.isBlank(b.title) || b.title.length > 300 || b.authors.isEmpty ||
        b.authors.exists(a => Calibre.isBlank(a) || a.toLowerCase(java.util.Locale.ROOT).contains("unknown"))

    def issues(r: AuditRecord, b: Book): List[AuditIssue] =
      List(
        Option.when(junk(b))(AuditIssue.Junk),
        Option.when(!junk(b) && !titleMatches(r.title, b.title))(AuditIssue.Title),
        Option.when(!junk(b) && !authorsMatch(r.authors, b.authors))(AuditIssue.Authors),
        Option.when(r.year.isDefined && year(b).isDefined && r.year != year(b))(AuditIssue.Year)
      ).flatten

    def audit(records: List[AuditRecord], books: Map[Int, Book]): List[AuditFinding] =
      records.groupBy(_.calibreId).toList.sortBy(_._1).flatMap { (id, rs) =>
        val keys = rs.map(_.citekey).sorted
        books.get(id) match
          case None    => List(AuditFinding(id, keys, List(AuditIssue.Missing), None, rs.minBy(_.citekey)))
          case Some(b) =>
            val (best, found) = rs.map(r => r -> issues(r, b)).minBy((r, is) => (is.size, r.citekey))
            if found.isEmpty then Nil else List(AuditFinding(id, keys, found, Some(b), best))
      }

  /** One book as Calibre holds it — the shape both readers below produce (`calibredb list --for-machine` and the
    * `sqlite3 -readonly` fallback normalise to this same record).
    */
  final case class Book(
      id: Int,
      title: String,
      authors: List[String],
      pubdate: Option[String],
      publisher: Option[String],
      tags: List[String],
      comments: Option[String],
      identifiers: Map[String, String],
      formats: List[String],
      /** The cover Calibre keeps beside the book (`<book dir>/cover.jpg`), absolute — `None` when the entry has none.
        * Read-only: `media covers` uploads a copy, the file stays as it is.
        */
      cover: Option[String] = None
  ):
    /** Calibre writes `0101-01-01` for "no date"; a year below [[CalibreBook.FirstYear]] is that placeholder, not a
      * publication year.
      */
    def year: Option[Int] = pubdate
      .map(_.take(4))
      .filter(_.forall(_.isDigit))
      .flatMap(_.toIntOption)
      .filter(_ >= Book.FirstYear)
    def doi: Option[String] = identifiers.get("doi")
    def isbn: Option[String] = identifiers.get("isbn")

    /** The file to import: the requested format, else PDF, else EPUB (both carry a text layer). */
    def pick(format: Option[String]): Option[String] =
      val wanted = format.map(_.toLowerCase).toList ++ List("pdf", "epub")
      wanted.iterator.map(ext => formats.find(_.toLowerCase.endsWith("." + ext))).collectFirst { case Some(f) =>
        f
      }

  object Book:
    /** The first year a pubdate is taken as one: Calibre's "no date" is year 101, and the library cites nothing older
      * by its printing (the host's citekeys use the same bound).
      */
    val FirstYear: Int = 1000

  /** One field `calibre update` changes: what Calibre has now and what it writes. */
  final case class Change(field: Field, from: String, to: String)

  /** A field `calibre update` writes, by its `calibredb set_metadata --field` name. */
  enum Field(val name: String):
    case Title extends Field("title")
    case Authors extends Field("authors")
    case Pubdate extends Field("pubdate")
    case Identifiers extends Field("identifiers")
    case Tags extends Field("tags")
    case Publisher extends Field("publisher")
    case Comments extends Field("comments")

  /** Where the library is and how it is reached, resolved from the plugin's settings: the library's folder (`~`
    * expanded), the `calibredb` and `sqlite3` to run, and the process whose presence means the Calibre GUI holds the
    * library.
    */
  final case class Library(library: String, calibredb: String, sqlite: String, guiProcess: String)

  /** What `calibre push` sends into Calibre for one source. */
  final case class Push(
      title: String,
      authors: List[String],
      doi: Option[String] = None,
      isbn: Option[String] = None,
      year: Option[Int] = None,
      publisher: Option[String] = None,
      summary: Option[String] = None,
      tags: List[String] = Nil
  )

  /** The `calibre` plugin's block (`histmersion.plugins.calibre`).
    *
    * @param libraryPath
    *   the library's folder, the one holding `metadata.db` (`~` is the home directory)
    * @param calibredb
    *   Calibre's command-line tool: a bare name is looked up on the server's `PATH`, then where Calibre installs it
    *   ([[CalibreSettings.InstallDirs]]), since a LaunchAgent's `PATH` holds only the system directories
    * @param sqlite
    *   the `sqlite3` the read-only fallback runs while the Calibre GUI holds the library
    * @param writeBack
    *   whether the server may write to the library (push, sync, update, set); off makes the collection read-only
    * @param guiProcess
    *   the process whose presence means the Calibre GUI holds the library
    */
  final case class Settings(
      libraryPath: String,
      calibredb: String,
      sqlite: String,
      writeBack: Boolean,
      guiProcess: String
  ) derives ConfigReader:
    /** The settings as the library is reached, `~` read against `home`, a bare `calibredb` found on `searchPath` or in
      * Calibre's install directories (kept bare when it is in none, so the failure names it as configured).
      */
    def library(
        home: Option[String],
        searchPath: Option[String] = None,
        executable: Path => Boolean = Files.isExecutable(_)
    ): Library =
      val path = libraryPath.trim match
        case "~"                                       => home.getOrElse("~")
        case p if p.startsWith("~/") && home.isDefined => home.getOrElse("") + p.drop(1)
        case p                                         => p
      Library(path, Settings.locate(calibredb, home, searchPath, executable), sqlite, guiProcess)

  object Settings:

    /** Where Calibre's installers put `calibredb`: the macOS app (system-wide or the user's), Homebrew on Apple silicon
      * and Intel, and the Linux installer's `/opt/calibre`.
      */
    val InstallDirs: List[String] = List(
      "/Applications/calibre.app/Contents/MacOS",
      "~/Applications/calibre.app/Contents/MacOS",
      "/opt/homebrew/bin",
      "/usr/local/bin",
      "/opt/calibre",
      "/usr/bin"
    )

    /** `name` as configured when it holds a `/`; otherwise the first executable `name` on `searchPath`, then in
      * [[InstallDirs]].
      */
    def locate(name: String, home: Option[String], searchPath: Option[String], executable: Path => Boolean): String =
      if name.contains('/') || name.isBlank then name
      else
        val dirs = searchPath.toList.flatMap(_.split(':').toList).filter(_.nonEmpty) ++
          InstallDirs.flatMap(d => if d.startsWith("~/") then home.map(_ + d.drop(1)).toList else List(d))
        dirs.iterator.map(d => Path.of(d, name)).find(executable).fold(name)(_.toString)
