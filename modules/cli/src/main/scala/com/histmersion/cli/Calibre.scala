package com.histmersion.cli

import com.histmersion.api.{Acquire, CalibreGap, Plural}
import com.histmersion.config.CalibreCliConfig

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
    formats: List[String],
    /** The cover Calibre keeps beside the book (`<book dir>/cover.jpg`), absolute — `None` when the
      * entry has none. Read-only: `media covers` uploads a copy, the file stays as it is. */
    cover: Option[String] = None
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
      List("--for-machine", "--fields", "id,title,authors,pubdate,publisher,tags,comments,identifiers,formats,cover",
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

  /** Bring an EXISTING entry in line with the corpus record (#831) — for when a record's title or
    * authors change after the push (a chapter split out of a volume left the volume's entry titled as
    * the chapter), and to leave the entry complete: identifiers merged, tags = the record's topics,
    * the record's year. What is written is [[Calibre.updatePlan]]; nothing is run when the entry
    * already agrees. Returns what changed. */
  def update(id: Int, meta: CalibrePush): Either[String, List[CalibreChange]] =
    for
      _       <- Calibre.guard(meta.title, Calibre.normaliseAuthors(meta.authors))
      _       <- Either.cond(!guiRunning, (), GuiRunning)
      cur     <- byId(id).flatMap(_.toRight(s"there is no Calibre entry $id"))
      changes <- Calibre.updatePlan(meta, cur)
      _       <- write(id, changes)
    yield changes

  /** `calibre update --all` (#831): plan the update of every linked entry against ONE listing of the
    * library; with `apply`, write the entries that would change. An entry linked from more than one
    * record (a chapter and its volume) is left alone — which record it should show is a decision for
    * a person, `calibre update <citekey>`. */
  def updateAll(linked: List[LinkedRecord], apply: Boolean): Either[String, List[UpdateEntry]] =
    for
      _     <- Either.cond(!apply || !guiRunning, (), GuiRunning)
      books <- all()
    yield
      Calibre.planAll(linked, books.map(b => b.id -> b).toMap).map { e =>
        e.outcome match
          case UpdateOutcome.Change(cs) if apply =>
            e.copy(outcome = write(e.calibreId, cs).fold(UpdateOutcome.Failed(cs, _), _ => UpdateOutcome.Applied(cs)))
          case _ => e
      }

  /** One `set_metadata` with the planned fields; nothing at all when there are none. */
  private def write(id: Int, changes: List[CalibreChange]): Either[String, Unit] =
    if changes.isEmpty then Right(())
    else shell.run(Calibre.setMetadataCmd(cfg.calibredb, libArgs, id, changes)).map(_ => ())

  private val GuiRunning =
    "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)"

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
      List("--title", meta.title.trim, "--authors", meta.authors.mkString(" & "), "--duplicates") ++
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

/** A field `calibre update` writes, by its `calibredb set_metadata --field` name. */
enum CalibreField(val name: String):
  case Title       extends CalibreField("title")
  case Authors     extends CalibreField("authors")
  case Pubdate     extends CalibreField("pubdate")
  case Identifiers extends CalibreField("identifiers")
  case Tags        extends CalibreField("tags")
  case Publisher   extends CalibreField("publisher")
  case Comments    extends CalibreField("comments")

/** One field `calibre update` changes: what Calibre has now and what it writes. */
final case class CalibreChange(field: CalibreField, from: String, to: String)

/** A source linked to a Calibre entry, with what the corpus would send there. */
final case class LinkedRecord(citekey: String, calibreId: Int, meta: CalibrePush)

/** What `calibre update --all` does, or did, to one Calibre entry. */
enum UpdateOutcome:
  /** Dry run: these fields would change. */
  case Change(changes: List[CalibreChange])
  case Applied(changes: List[CalibreChange])
  case Failed(changes: List[CalibreChange], reason: String)
  case UpToDate
  /** Linked from more than one record — which one it should show is a person's decision. */
  case Shared
  /** No such entry in Calibre. */
  case Missing
  /** The record itself fails the metadata guard. */
  case Refused(reason: String)

final case class UpdateEntry(calibreId: Int, citekeys: List[String], outcome: UpdateOutcome)

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
    // `López-Ruiz` gives `lopezruiz`, not `lpezruiz` (#830): Acquire.citekey folds accents itself (#878)
    Acquire.citekey(b.authors, b.year, Some(b.title)) +
      volumeOf(b.title).fold("")(n => s"_v$n")

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

  /** The summary an import writes (#896): Calibre's description only fills an empty one — a re-import
    * into an existing record never replaces a summary an agent compiled with the (often truncated)
    * Calibre comments. `None` leaves the record's summary alone. */
  def importSummary(fromCalibre: Option[String], current: Option[String]): Option[String] =
    if current.exists(_.trim.nonEmpty) then None else fromCalibre

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
  /** What [[Calibre.update]] writes into `current` from the record, field by field, and only where
    * it differs (#831):
    *   - title, and the authors as "First Last" with no role or life dates ([[normaliseAuthor]]);
    *   - the record's YEAR, always (the user's decision of 2026-10-03: Calibre shows the year the
    *     corpus cites, not the printing on the shelf);
    *   - identifiers MERGED: the record's DOI is set, its ISBN added only when Calibre has none (an
    *     ISBN names the edition on the shelf), and every other scheme (google, asin, …) is kept;
    *   - tags = the record's topics, when it has any;
    *   - publisher and description only where Calibre has NONE: Calibre's describe the edition on
    *     the shelf, and a store's description is the user's data, not ours to replace.
    * Languages are not written: the corpus records no language for a source.
    * `Left` when the record would put a blank or Unknown title/author into Calibre. */
  def updatePlan(meta: CalibrePush, current: CalibreBook): Either[String, List[CalibreChange]] =
    val authors = normaliseAuthors(meta.authors)
    guard(meta.title, authors).map { _ =>
      def empty(v: Option[String]) = v.forall(s => isBlank(s) || s.trim == "-")
      def change(f: CalibreField, from: String, to: String) = Option.when(from != to)(CalibreChange(f, from, to))
      val ids = mergeIdentifiers(current.identifiers, meta.doi, meta.isbn)
      List(
        // compared and written trimmed (#883): a stray space is not a different title
        Option.when(current.title.trim != meta.title.trim)(CalibreChange(CalibreField.Title, current.title, meta.title.trim)),
        change(CalibreField.Authors, current.authors.mkString(" & "), authors.mkString(" & ")),
        meta.year.flatMap(y => Option.when(CalibreAudit.year(current) != Some(y))(
          CalibreChange(CalibreField.Pubdate, CalibreAudit.year(current).fold("")(_.toString), s"$y-01-01"))),
        Option.when(ids != current.identifiers)(
          CalibreChange(CalibreField.Identifiers, identifiersValue(current.identifiers), identifiersValue(ids))),
        Option.when(meta.tags.nonEmpty && meta.tags.distinct.sorted != current.tags.distinct.sorted)(
          CalibreChange(CalibreField.Tags, current.tags.mkString(","), meta.tags.distinct.mkString(","))),
        meta.publisher.filter(p => empty(current.publisher) && !isBlank(p))
          .map(CalibreChange(CalibreField.Publisher, current.publisher.getOrElse(""), _)),
        meta.summary.filter(d => empty(current.comments) && !isBlank(d))
          .map(CalibreChange(CalibreField.Comments, current.comments.getOrElse(""), _))
      ).flatten
    }

  /** `calibredb set_metadata` writing `changes` into entry `id`. */
  def setMetadataCmd(calibredb: String, libArgs: List[String], id: Int, changes: List[CalibreChange]): List[String] =
    List(calibredb, "set_metadata") ++ libArgs ++ List(id.toString) ++
      changes.flatMap(c => List("--field", s"${c.field.name}:${c.to}"))

  /** Calibre's identifiers with the record's merged in: the DOI is the record's (it names the work
    * the corpus cites), an ISBN only fills a gap, and nothing Calibre has is ever dropped. A DOI
    * is case-insensitive, so one differing only in case is left as Calibre has it (#883). A value
    * with a comma cannot cross `--field identifiers:` (the comma separates the pairs), so it is
    * left out. */
  def mergeIdentifiers(current: Map[String, String], doi: Option[String], isbn: Option[String]): Map[String, String] =
    def ok(v: String) = v.trim.nonEmpty && !v.contains(",")
    val sameDoi = (d: String) => current.get("doi").exists(_.trim.equalsIgnoreCase(d))
    current ++ doi.map(_.trim).filter(ok).filterNot(sameDoi).map("doi" -> _) ++
      isbn.map(_.trim).filter(ok).filterNot(_ => current.get("isbn").exists(_.trim.nonEmpty)).map("isbn" -> _)

  /** `type:value,type:value`, as `--field identifiers:` takes it, in a stable order. */
  def identifiersValue(ids: Map[String, String]): String =
    ids.toList.sortBy(_._1).map((k, v) => s"$k:$v").mkString(",")

  /** Role words a library or a publisher appends to a name: `eds.`, `trans.`, `Hrsg.`. The short
    * ones need their dot — `Smith, Ed` is a man called Ed. */
  private val Role =
    """(?i)[,;]?\s*\b(?:ed\.|eds\.?|editors?|edited\s+by|trans\.|transl\.|translators?|translated\s+by|tr\.|comp\.|compilers?|hrsg\.?|hg\.|red\.)\s*$""".r
  /** A life span or a library's date qualifier at the end of a name: `, 1921-1994`, `, b. 1950`. */
  private val LifeDates =
    """(?i)[,\s]+(?:(?:b|d|fl|ca|c)\.\s*)?\d{3,4}\??(?:\s*[-–]\s*(?:\d{3,4}\??)?)?\.?\s*$""".r
  private val Suffix = Set("jr", "jr.", "sr", "sr.", "ii", "iii", "iv")

  /** One author as Calibre should show it (#831): "First Last", without a bracketed role or life
    * span, a trailing role word or library-style dates. `Clunies Ross, Margaret (ed.)` →
    * `Margaret Clunies Ross`; `King, Martin Luther, Jr.` → `Martin Luther King Jr.`. A name with more
    * commas than that is not one "Last, First" and is left in its order. */
  def normaliseAuthor(raw: String): String =
    val unbracketed = raw.replaceAll("\\([^)]*\\)|\\[[^\\]]*\\]", " ")
    val bare = Iterator.iterate(unbracketed.trim)(s =>
        LifeDates.replaceFirstIn(Role.replaceFirstIn(s, ""), "").trim.stripSuffix(",").stripSuffix(";").trim)
      .sliding(2).collectFirst { case Seq(a, b) if a == b => a }.getOrElse(unbracketed.trim)
    val parts = bare.split(",").map(_.trim).filter(_.nonEmpty).toList
    val ordered = parts match
      case List(last, first)                                       => s"$first $last"
      case List(last, first, sfx) if Suffix(sfx.toLowerCase)       => s"$first $last $sfx"
      case _                                                       => bare
    ordered.replaceAll("\\s+", " ").trim

  /** [[normaliseAuthor]] over a list, dropping names that end up empty and repeats. */
  def normaliseAuthors(authors: List[String]): List[String] =
    authors.map(normaliseAuthor).filter(_.nonEmpty).distinct

  /** Plan `update --all`: one [[UpdateEntry]] per linked Calibre id, in id order. */
  def planAll(linked: List[LinkedRecord], books: Map[Int, CalibreBook]): List[UpdateEntry] =
    linked.groupBy(_.calibreId).toList.sortBy(_._1).map { (id, rs) =>
      val keys = rs.map(_.citekey).distinct.sorted
      val outcome =
        if keys.sizeIs > 1 then UpdateOutcome.Shared
        else books.get(id) match
          case None    => UpdateOutcome.Missing
          case Some(b) => updatePlan(rs.head.meta, b).fold(UpdateOutcome.Refused(_),
                            cs => if cs.isEmpty then UpdateOutcome.UpToDate else UpdateOutcome.Change(cs))
      UpdateEntry(id, keys, outcome)
    }

  /** `update --all` as Markdown: what changes (or changed), then what was left alone and why. */
  def markdownUpdateAll(entries: List[UpdateEntry], applied: Boolean): String =
    def cell(s: String) = s.replace("|", "\\|").replace("\n", " ").take(80)
    val changing = entries.collect { e => e.outcome match
      case UpdateOutcome.Change(cs)     => (e, cs, "")
      case UpdateOutcome.Applied(cs)    => (e, cs, "written")
      case UpdateOutcome.Failed(cs, why) => (e, cs, s"FAILED: $why")
    }
    val rows = changing.flatMap { (e, cs, note) =>
      cs.map(c => s"| ${e.calibreId} | ${e.citekeys.mkString(", ")} | ${c.field.name} | ${cell(c.from)} | ${cell(c.to)} | $note |")
    }
    val left = entries.collect { e => e.outcome match
      case UpdateOutcome.Shared       => s"- ${e.calibreId}: linked from ${e.citekeys.mkString(", ")} — update one by hand (`histmersion calibre update <citekey>`)"
      case UpdateOutcome.Missing      => s"- ${e.calibreId}: no such Calibre entry (${e.citekeys.mkString(", ")}) — re-push, or clear the source's calibre id"
      case UpdateOutcome.Refused(why) => s"- ${e.calibreId} (${e.citekeys.mkString(", ")}): $why"
    }
    val upToDate = entries.count(_.outcome == UpdateOutcome.UpToDate)
    val head =
      if changing.isEmpty then s"_No linked Calibre entry needs updating ($upToDate up to date)._"
      else
        val verb = if applied then "updated" else "would change"
        (List(s"### ${changing.size} Calibre ${com.histmersion.api.Plural.noun(changing.size, "entry", "entries")} $verb · $upToDate up to date", "",
          "| id | citekey | field | Calibre | record | |", "|---:|---|---|---|---|---|") ++ rows).mkString("\n")
    val tail = if left.isEmpty then "" else s"\n\n**Left alone (${left.size})**\n\n${left.mkString("\n")}"
    val hint = if !applied && changing.nonEmpty then "\n\n_Dry run. Re-run with `--apply` (Calibre GUI closed)._" else ""
    head + tail + hint

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
    if out.trim.isEmpty then Right(Nil) // no match: nothing is printed at all (#967)
    else io.circe.parser.parse(out).left.map(e => s"calibredb returned unparseable JSON: ${e.getMessage}")
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
            formats = c.downField("formats").as[List[String]].getOrElse(Nil),
            cover = c.downField("cover").as[String].toOption.map(_.trim).filter(_.nonEmpty))
        }
      })

  /** `sqlite3 -json`: every column is a scalar, the multi-valued ones joined by [[sqliteQuery]], and
    * `formats` relative to the library root. */
  def parseSqliteJson(out: String, libraryDir: String): Either[String, List[CalibreBook]] =
    // `sqlite3 -json` prints NOTHING for an empty result, not `[]` — that was "exhausted input" (#967)
    if out.trim.isEmpty then Right(Nil)
    else io.circe.parser.parse(out).left.map(e => s"sqlite3 returned unparseable JSON: ${e.getMessage}")
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
              .map(f => Path.of(libraryDir, f).toString),
            cover = s("cover").map(f => Path.of(libraryDir, f).toString))
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
       |    FROM data d WHERE d.book = b.id) AS formats,
       | CASE WHEN b.has_cover THEN b.path || '/cover.jpg' END AS cover
       |FROM books b WHERE $where ORDER BY b.id DESC LIMIT ${limit.max(1)}""".stripMargin
      .replace("\n", " ")

  /** The host-side original of a source, when a directory of fetched files is configured:
    * `<originals>/<citekey>/paper.*` — an optional override of the original the server stored, which
    * `calibre push`/`sync` download otherwise ([[pushFile]]). */
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

  /** Where a push takes its file from: `--file`, else the host-side original under
    * `cli.calibre.originals-dir`, else the original the server stored (downloaded for the push). */
  enum PushFile:
    case Given(path: Path)
    case Local(path: Path)
    case Server

  def pushFile(explicit: Option[Path], local: => Option[Path]): PushFile =
    explicit.map(PushFile.Given(_)).orElse(local.map(PushFile.Local(_))).getOrElse(PushFile.Server)

  /** Where the file a push would add comes from, as `calibre gaps`/`sync` report it (`original`). */
  enum GapOriginal(val label: String):
    /** `<cli.calibre.originals-dir>/<citekey>/paper.*` on this host. */
    case Local extends GapOriginal("local")
    /** The original the server stores (`GET /api/sources/{ck}/original`). */
    case Stored extends GapOriginal("stored")
    /** The server did not say (an older server): counted, and the push tries. */
    case Unknown extends GapOriginal("unknown")
    /** No file anywhere — a metadata-only record (a citation-graph stub, a record with an abstract
      * and no fetched file). Nothing to push, so not a Calibre gap: it belongs on the acquire list. */
    case Missing extends GapOriginal("none")

  /** One source the server lists as not in Calibre, with where its original is and whether it
    * COUNTS as a gap — the `calibre gaps --quiet` reminder and `calibre sync` count only these. */
  final case class GapEntry(gap: CalibreGap, original: GapOriginal, counted: Boolean)

  /** Pure: a gap counts when there is an original to push (on this host, or stored on the server)
    * and the source is not already canonical in Calibre (provenance `calibre`, or a Calibre id — the
    * server leaves both out; an older or differently-cased row is caught here too). A record with no
    * original anywhere is listed, never counted. */
  def classify(gaps: List[CalibreGap], local: String => Option[Path]): List[GapEntry] =
    gaps.map { g =>
      val original =
        if local(g.citekey).isDefined then GapOriginal.Local
        else g.storedOriginal match
          case Some(true)  => GapOriginal.Stored
          case Some(false) => GapOriginal.Missing
          case None        => GapOriginal.Unknown
      val inCalibre = g.calibreId.exists(_.trim.nonEmpty) ||
        g.provenance.exists(_.trim.toLowerCase(java.util.Locale.ROOT) == "calibre")
      GapEntry(g, original, counted = original != GapOriginal.Missing && !inCalibre)
    }

  /** The informational line for the records with no fetched file — never a gap. */
  def uncountedLine(n: Int): String =
    s"$n ${Plural.noun(n, "record")} without a fetched file — not counted; see the acquire list " +
      "(`histmersion queue acquire`); `--json` lists them."

  /** `calibre gaps` as Markdown: the counted gaps as a table, then one line for the records with no
    * fetched file (never rows of the table: they are not gaps). */
  def markdownGaps(entries: List[GapEntry]): String =
    def esc(s: String) = s.replace("|", "\\|").replace("\n", " ").trim
    val counted = entries.filter(_.counted)
    val missing = entries.count(_.original == GapOriginal.Missing)
    val head =
      if counted.isEmpty then "_Every source with a fetched file is in Calibre._"
      else
        val rows = counted.map { e =>
          val g = e.gap
          s"| `${g.citekey}` | ${esc(g.title).take(70)} | ${g.year.fold("—")(_.toString)} " +
            s"| ${esc(g.authors.take(2).mkString(", "))} | ${esc(g.provenance.getOrElse("—"))} | ${e.original.label} |"
        }
        (List(s"### ${counted.size} source(s) not in Calibre", "",
            "| citekey | title | year | authors | provenance | original |", "|---|---|---|---|---|---|")
          ++ rows ++ List("", "_Push them with `histmersion calibre sync --apply`: the file is " +
            "`<cli.calibre.originals-dir>/<citekey>/paper.*` on this host (`local`), else the original the server " +
            "stored (`stored`)._")).mkString("\n")
    if missing == 0 then head else s"$head\n\n${uncountedLine(missing)}"

  /** `calibre search --unlinked` (#967): the entries no source links (`sources.calibre_id`), newest
    * first — Calibre ids grow as books are added, so the highest id is the latest addition. */
  def unlinked(books: List[CalibreBook], linked: Set[Int], limit: Int): List[CalibreBook] =
    books.filterNot(b => linked.contains(b.id)).sortBy(-_.id).take(limit.max(1))

  /** The unlinked listing as Markdown: one line per entry with its file path(s), the way a person
    * finds the books they added that are not in the corpus yet. */
  def markdownUnlinked(books: List[CalibreBook], total: Int): String =
    if books.isEmpty then "_Every Calibre entry is linked to a source._"
    else
      val lines = books.map { b =>
        val who  = b.authors.take(3).mkString(", ") + (if b.authors.sizeIs > 3 then " et al." else "")
        val year = b.year.fold("")(y => s" ($y)")
        val files = if b.formats.isEmpty then "  - no file" else b.formats.map(f => s"  - `$f`").mkString("\n")
        s"- **${b.id}** · ${b.title}$year — ${if who.isEmpty then "no author" else who}\n$files"
      }
      val shown = if total > books.size then s"${books.size} of $total" else books.size.toString
      (List(s"### $shown Calibre ${com.histmersion.api.Plural.noun(total, "entry", "entries")} not linked to any source (newest first)", "")
        ++ lines ++ List("", "_Import one with `histmersion calibre import <id>`, or link it to an existing record with " +
          "`histmersion calibre link <citekey> <id>`._")).mkString("\n")

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
