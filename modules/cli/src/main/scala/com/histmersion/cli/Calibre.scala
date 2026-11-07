package com.histmersion.cli

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Plural
import com.histmersion.api.library.{Acquire, CalibreGap}
import com.histmersion.config.CalibreCliConfig

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Acquire
import com.histmersion.api.library.CollectionAudit
import com.histmersion.api.library.{CollectionEntry, CollectionGapRow, CollectionGaps, CollectionUpdateRow}
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import java.text.Normalizer

/** Host-side Calibre integration — the ONE local integration the CLI is allowed, because the Calibre library lives on
  * the user's machine and the server (possibly a container) cannot see it.
  *
  * Reading happens two ways, in this order:
  *   1. `calibredb list --for-machine`, which is Calibre's own supported interface;
  *   2. `sqlite3 -readonly -json` straight against `metadata.db`, for when the Calibre GUI holds the database and
  *      `calibredb` refuses.
  *
  * Both are READ-ONLY. `metadata.db` is opened with `-readonly` precisely so a bug here can never damage the user's
  * library; the only writes this file performs are the explicit `calibredb add` / `set_metadata` of a `push`, which
  * additionally refuse to run while the GUI is open.
  */
final class Calibre(cfg: CalibreCliConfig, shell: Shell = Shell.System):

  private def libArgs = List("--with-library", cfg.library)

  /** The Calibre GUI holds an exclusive lock on the library for writes. */
  def guiRunning: Boolean = shell.isRunning("/Applications/calibre.app/Contents/MacOS/calibre")

  def libraryExists: Boolean = Files.isRegularFile(Path.of(cfg.library, "metadata.db"))

  // ── reading ─────────────────────────────────────────────────────────────────────────────────

  def search(query: com.histmersion.api.Api.Search.Text, limit: com.histmersion.api.Paging.Limit = 20): Either[com.histmersion.cli.Calibre.Problem, List[com.histmersion.cli.Calibre.Book]] =
    viaCalibredb(Some(query), limit).orElse(viaSqlite(Some(query), None, limit))

  /** The whole library (read-only), for the audit: one listing instead of a call per entry. */
  def all(limit: com.histmersion.api.Paging.Limit = 1000000): Either[com.histmersion.cli.Calibre.Problem, List[com.histmersion.cli.Calibre.Book]] =
    viaCalibredb(None, limit).orElse(viaSqlite(None, None, limit))

  def byId(id: com.histmersion.cli.Calibre.Book.Id): Either[com.histmersion.cli.Calibre.Problem, Option[com.histmersion.cli.Calibre.Book]] =
    viaCalibredb(Some(s"id:$id"), 1)
      .map(_.find(_.id == id))
      .orElse(viaSqlite(None, Some(id), 1).map(_.headOption))

  private def viaCalibredb(query: Option[com.histmersion.api.Api.Search.Text], limit: com.histmersion.api.Paging.Limit): Either[com.histmersion.cli.Calibre.Problem, List[com.histmersion.cli.Calibre.Book]] =
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
      query: Option[com.histmersion.api.library.Query.Statement],
      id: Option[com.histmersion.cli.Calibre.Book.Id],
      limit: com.histmersion.api.Paging.Limit
  ): Either[com.histmersion.cli.Calibre.Problem, List[com.histmersion.cli.Calibre.Book]] =
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
  def add(file: Path, meta: com.histmersion.cli.Calibre.Push): Either[com.histmersion.cli.Calibre.Problem, com.histmersion.cli.Calibre.Book.Id] =
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

  /** Bring an EXISTING entry in line with the corpus record (#831) — for when a record's title or authors change after
    * the push (a chapter split out of a volume left the volume's entry titled as the chapter), and to leave the entry
    * complete: identifiers merged, tags = the record's topics, the record's year. What is written is
    * [[Calibre.updatePlan]]; nothing is run when the entry already agrees. Returns what changed.
    */
  def update(
      id: com.histmersion.cli.Calibre.Book.Id,
      meta: com.histmersion.cli.Calibre.Push
  ): Either[com.histmersion.cli.Calibre.Problem, List[com.histmersion.cli.Calibre.Change]] =
    for
      _ <- Calibre.guard(meta.title, Calibre.normaliseAuthors(meta.authors))
      _ <- Either.cond(!guiRunning, (), GuiRunning)
      cur <- byId(id).flatMap(_.toRight(s"there is no Calibre entry $id"))
      changes <- Calibre.updatePlan(meta, cur)
      _ <- write(id, changes)
    yield changes

  /** `calibre update --all` (#831): plan the update of every linked entry against ONE listing of the library; with
    * `apply`, write the entries that would change. An entry linked from more than one record (a chapter and its volume)
    * is left alone — which record it should show is a decision for a person, `calibre update <citekey>`.
    */
  def updateAll(linked: List[LinkedRecord], apply: com.histmersion.api.library.CollectionSync.Request.Apply): Either[com.histmersion.cli.Calibre.Problem, List[UpdateEntry]] =
    for
      _ <- Either.cond(!apply || !guiRunning, (), GuiRunning)
      books <- all()
    yield Calibre.planAll(linked, books.map(b => b.id -> b).toMap).map { e =>
      e.outcome match
        case UpdateOutcome.Change(cs) if apply =>
          e.modify(_.outcome)
            .setTo(write(e.calibreId, cs).fold(UpdateOutcome.Failed(cs, _), _ => UpdateOutcome.Applied(cs)))
        case _ => e
    }

  /** One `set_metadata` with the planned fields; nothing at all when there are none. */
  private def write(id: com.histmersion.cli.Calibre.Book.Id, changes: List[com.histmersion.cli.Calibre.Change]): Either[com.histmersion.cli.Calibre.Problem, Unit] =
    if changes.isEmpty then Right(())
    else shell.run(Calibre.setMetadataCmd(cfg.calibredb, libArgs, id, changes)).map(_ => ())

  private val GuiRunning =
    "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)"

  /** Set named fields on an existing entry — the explicit, reviewed counterpart of [[update]] (`enrich-calibre` applies
    * approved fixes with it). Same guards as every other write.
    */
  def set(id: com.histmersion.cli.Calibre.Book.Id, fields: List[(com.histmersion.api.library.CollectionFieldValue.Name, com.histmersion.cli.Calibre.SetFieldsPart2)]): Either[com.histmersion.cli.Calibre.Problem, Unit] =
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

  /** Replace the file of an EXISTING entry (#652): `calibredb add_format`, which replaces the entry's format of the
    * same type. Unlike [[add]] it never creates an entry, so the link stays valid.
    */
  def replaceFormat(id: com.histmersion.cli.Calibre.Book.Id, file: Path): Either[com.histmersion.cli.Calibre.Problem, com.histmersion.cli.Calibre.Book.Format] =
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

  private def addCmd(file: Path, meta: com.histmersion.cli.Calibre.Push): List[com.histmersion.cli.Calibre.CommandArg] =
    List(cfg.calibredb, "add", file.toString) ++ libArgs ++
      List("--title", meta.title.trim, "--authors", meta.authors.mkString(" & "), "--duplicates") ++
      meta.doi.toList.flatMap(d => List("--identifier", s"doi:$d")) ++
      meta.isbn.toList.flatMap(i => List("--isbn", i)) ++
      (if meta.tags.isEmpty then Nil else List("--tags", meta.tags.mkString(",")))

  /** The fields `calibredb add` cannot set. Best-effort: a failure here leaves a usable entry. */
  private def setMetadata(id: com.histmersion.cli.Calibre.Book.Id, meta: com.histmersion.cli.Calibre.Push): Either[com.histmersion.cli.Calibre.Problem, Unit] =
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
object Calibre:
  type AuthorsText = AuthorsText.T
  object AuthorsText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommandArg = CommandArg.T
  object CommandArg extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type File = File.T
  object File extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LibraryDir = LibraryDir.T
  object LibraryDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkedCount = LinkedCount.T
  object LinkedCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Markdown = Markdown.T
  object Markdown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalsDir = OriginalsDir.T
  object OriginalsDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Values that mean "nobody filled this in". Pushing one of these into Calibre creates an entry nobody will ever find
    * again, so both `import` and `push` refuse.
    */

  type SetFieldsPart2 = SetFieldsPart2.T
  object SetFieldsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceableFormats = ReplaceableFormatsValue
  type ReplaceableFormatsValue = ReplaceableFormatsValue.T
  object ReplaceableFormatsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeIdentifiersResultPart2 = MergeIdentifiersResultPart2.T
  object MergeIdentifiersResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeIdentifiersCurrentPart2 = MergeIdentifiersCurrentPart2.T
  object MergeIdentifiersCurrentPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentifiersValueResult = IdentifiersValueResult.T
  object IdentifiersValueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentifiersValueIdsPart2 = IdentifiersValueIdsPart2.T
  object IdentifiersValueIdsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettableFields = SettableFieldsValue
  type SettableFieldsValue = SettableFieldsValue.T
  object SettableFieldsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetCmdFieldsPart2 = SetCmdFieldsPart2.T
  object SetCmdFieldsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Blank = Set("", "unknown", "unknown author", "untitled", "n/a", "?", "none")

  def isBlank(s: String): Boolean = Blank.contains(Option(s).getOrElse("").trim.toLowerCase)

  /** The metadata guard, shared by `import` (Calibre → corpus) and `push` (corpus → Calibre). */
  def guard(title: com.histmersion.api.library.Title, authors: List[com.histmersion.api.library.AuthorName]): Either[com.histmersion.cli.Calibre.Problem, Unit] =
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

  private val Volume = """(?i)\b(?:volume\s*|vol\.\s*|vol\s+)([0-9]+|[ivxlc]+)\b""".r

  /** The volume a title names — `Volume I`, `Vol. 2`, `volume 3` — so two volumes of one work get two citekeys.
    */
  def volumeOf(title: com.histmersion.api.library.Title): Option[com.histmersion.util.storage.Store.Volume.Number] =
    Volume.findFirstMatchIn(title).flatMap(m => m.group(1).toIntOption.orElse(roman(m.group(1)))).filter(_ > 0)

  private def roman(s: String): Option[com.histmersion.util.storage.Store.Volume.Number] =
    val v = Map('i' -> 1, 'v' -> 5, 'x' -> 10, 'l' -> 50, 'c' -> 100)
    val ds = s.toLowerCase.toList.map(v)
    Option.when(ds.nonEmpty)(ds.zipAll(ds.drop(1), 0, 0).map((a, b) => if a < b then -a else a).sum)

  /** The citekey `calibre import` generates: the corpus convention ([[Acquire.citekey]]) plus `_v<n>` for a numbered
    * volume.
    */
  def citekeyFor(b: com.histmersion.cli.Calibre.Book): com.histmersion.api.library.Citekey =
    // `López-Ruiz` gives `lopezruiz`, not `lpezruiz` (#830): Acquire.citekey folds accents itself (#878)
    Acquire.citekey(b.authors, b.year, Some(b.title)) +
      volumeOf(b.title).fold("")(n => s"_v$n")

  /** Whether importing Calibre entry `id` under `ck` may go ahead (#714: two volumes once generated the same citekey
    * and were silently merged into one record).
    *
    * @param explicit
    *   the user passed `--citekey`, so landing on an existing record is deliberate
    * @param existing
    *   `None` when no record has `ck`; otherwise that record's Calibre id
    */
  def importTarget(
      ck: com.histmersion.api.library.Citekey,
      id: com.histmersion.cli.Calibre.Book.Id,
      explicit: com.histmersion.cli.ImportTarget.Explicit,
      existing: Option[Option[com.histmersion.api.library.Citekey]]
  ): Either[com.histmersion.cli.Calibre.Problem, ImportTarget] =
    existing match
      case None                                        => Right(ImportTarget.Create)
      case Some(Some(linked)) if linked == id.toString => Right(ImportTarget.Update(None))
      case Some(linked) if explicit                    => Right(ImportTarget.Update(linked))
      case Some(linked)                                =>
        val what = linked.fold("a record that is not linked to Calibre")(l => s"the record of Calibre id $l")
        Left(
          s"the generated citekey '$ck' is already $what — importing Calibre id $id would merge two " +
            s"works into one record. Pass --citekey <another key> for a separate record, or --citekey $ck " +
            "to import into that record on purpose"
        )

  /** The summary an import writes (#896): Calibre's description only fills an empty one — a re-import into an existing
    * record never replaces a summary an agent compiled with the (often truncated) Calibre comments. `None` leaves the
    * record's summary alone.
    */
  def importSummary(fromCalibre: Option[com.histmersion.api.library.Source.Summary], current: Option[com.histmersion.api.library.Source.Summary]): Option[com.histmersion.api.library.Source.Summary] =
    if current.exists(_.trim.nonEmpty) then None else fromCalibre

  /** The formats `calibre push --replace` puts into an entry: the two the corpus imports. */
  val ReplaceableFormats: List[com.histmersion.cli.Calibre.ReplaceableFormats] = List("pdf", "epub")

  def formatOf(file: Path): Either[com.histmersion.cli.Calibre.Problem, com.histmersion.cli.Calibre.Book.Format] =
    val ext = file.getFileName.toString.reverse.takeWhile(_ != '.').reverse.toLowerCase
    Either.cond(
      ReplaceableFormats.contains(ext),
      ext,
      s"'${file.getFileName}' is not a ${ReplaceableFormats.mkString(" or ")} file"
    )

  /** `calibredb add_format` replaces the entry's existing format of the same type (that is its default;
    * `--dont-replace` would be the opposite).
    */
  def replaceFormatCmd(calibredb: String, libArgs: List[String], id: com.histmersion.cli.Calibre.Book.Id, file: Path): List[String] =
    List(calibredb, "add_format") ++ libArgs ++ List(id.toString, file.toString)

  /** `calibredb add` prints `Added book ids: 1693`. */
  private val AddedIds = """Added book ids?:\s*([0-9,]+)""".r

  /** What [[Calibre.update]] writes into `current` from the record, field by field, and only where it differs (#831):
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
      meta: com.histmersion.cli.Calibre.Push,
      current: com.histmersion.cli.Calibre.Book
  ): Either[com.histmersion.cli.Calibre.Problem, List[com.histmersion.cli.Calibre.Change]] =
    val authors = normaliseAuthors(meta.authors)
    guard(meta.title, authors).map { _ =>
      def empty(v: Option[String]) = v.forall(s => isBlank(s) || s.trim == "-")
      def change(f: com.histmersion.cli.Calibre.Field, from: com.histmersion.api.library.CollectionFieldChange.From, to: com.histmersion.api.library.CollectionFieldChange.To) =
        Option.when(from != to)(com.histmersion.cli.Calibre.Change(f, from, to))
      val ids = mergeIdentifiers(current.identifiers, meta.doi, meta.isbn)
      List(
        // compared and written trimmed (#883): a stray space is not a different title
        Option.when(current.title.trim != meta.title.trim)(
          com.histmersion.cli.Calibre.Change(com.histmersion.cli.Calibre.Field.Title, current.title, meta.title.trim)
        ),
        change(com.histmersion.cli.Calibre.Field.Authors, current.authors.mkString(" & "), authors.mkString(" & ")),
        meta.year.flatMap(y =>
          Option.when(com.histmersion.cli.Calibre.Audit.year(current) != Some(y))(
            com.histmersion.cli.Calibre.Change(
              com.histmersion.cli.Calibre.Field.Pubdate,
              com.histmersion.cli.Calibre.Audit.year(current).fold("")(_.toString),
              s"$y-01-01"
            )
          )
        ),
        Option.when(ids != current.identifiers)(
          com.histmersion.cli.Calibre.Change(
            com.histmersion.cli.Calibre.Field.Identifiers,
            identifiersValue(current.identifiers),
            identifiersValue(ids)
          )
        ),
        Option.when(meta.tags.nonEmpty && meta.tags.distinct.sorted != current.tags.distinct.sorted)(
          com.histmersion.cli.Calibre.Change(
            com.histmersion.cli.Calibre.Field.Tags,
            current.tags.mkString(","),
            meta.tags.distinct.mkString(",")
          )
        ),
        meta.publisher
          .filter(p => empty(current.publisher) && !isBlank(p))
          .map(
            com.histmersion.cli.Calibre
              .Change(com.histmersion.cli.Calibre.Field.Publisher, current.publisher.getOrElse(""), _)
          ),
        meta.summary
          .filter(d => empty(current.comments) && !isBlank(d))
          .map(
            com.histmersion.cli.Calibre
              .Change(com.histmersion.cli.Calibre.Field.Comments, current.comments.getOrElse(""), _)
          )
      ).flatten
    }

  /** `calibredb set_metadata` writing `changes` into entry `id`. */
  def setMetadataCmd(
      calibredb: String,
      libArgs: List[String],
      id: com.histmersion.cli.Calibre.Book.Id,
      changes: List[com.histmersion.cli.Calibre.Change]
  ): List[String] =
    List(calibredb, "set_metadata") ++ libArgs ++ List(id.toString) ++
      changes.flatMap(c => List("--field", s"${c.field.name}:${c.to}"))

  /** Calibre's identifiers with the record's merged in: the DOI is the record's (it names the work the corpus cites),
    * an ISBN only fills a gap, and nothing Calibre has is ever dropped. A DOI is case-insensitive, so one differing
    * only in case is left as Calibre has it (#883). A value with a comma cannot cross `--field identifiers:` (the comma
    * separates the pairs), so it is left out.
    */
  def mergeIdentifiers(current: Map[com.histmersion.api.library.CollectionEntry.IdentifiersKey, com.histmersion.cli.Calibre.MergeIdentifiersCurrentPart2], doi: Option[com.histmersion.api.library.Doi], isbn: Option[com.histmersion.api.library.CalibreGap.Isbn]): Map[com.histmersion.api.library.CollectionEntry.IdentifiersKey, com.histmersion.cli.Calibre.MergeIdentifiersResultPart2] =
    def ok(v: String) = v.trim.nonEmpty && !v.contains(",")
    val sameDoi = (d: String) => current.get("doi").exists(_.trim.equalsIgnoreCase(d))
    current ++ doi.map(_.trim).filter(ok).filterNot(sameDoi).map("doi" -> _) ++
      isbn.map(_.trim).filter(ok).filterNot(_ => current.get("isbn").exists(_.trim.nonEmpty)).map("isbn" -> _)

  /** `type:value,type:value`, as `--field identifiers:` takes it, in a stable order. */
  def identifiersValue(ids: Map[com.histmersion.api.library.CollectionEntry.IdentifiersKey, com.histmersion.cli.Calibre.IdentifiersValueIdsPart2]): com.histmersion.cli.Calibre.IdentifiersValueResult =
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

  /** One author as Calibre should show it (#831): "First Last", without a bracketed role or life span, a trailing role
    * word or library-style dates. `Clunies Ross, Margaret (ed.)` → `Margaret Clunies Ross`; `King, Martin Luther, Jr.`
    * → `Martin Luther King Jr.`. A name with more commas than that is not one "Last, First" and is left in its order.
    */
  def normaliseAuthor(raw: com.histmersion.api.library.AuthorName): com.histmersion.api.library.AuthorName =
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
  def normaliseAuthors(authors: List[com.histmersion.api.library.AuthorName]): List[com.histmersion.api.library.AuthorName] =
    authors.map(normaliseAuthor).filter(_.nonEmpty).distinct

  /** Plan `update --all`: one [[UpdateEntry]] per linked Calibre id, in id order. */
  def planAll(linked: List[LinkedRecord], books: Map[com.histmersion.cli.Calibre.Book.Id, com.histmersion.cli.Calibre.Book]): List[UpdateEntry] =
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

  /** `update --all` as Markdown: what changes (or changed), then what was left alone and why. */
  def markdownUpdateAll(entries: List[UpdateEntry], applied: com.histmersion.api.library.CollectionUpdateAll.Applied): com.histmersion.cli.Calibre.Markdown =
    def cell(s: String) = s.replace("|", "\\|").replace("\n", " ").take(80)
    val changing = entries.collect { e =>
      e.outcome match
        case UpdateOutcome.Change(cs)      => (e, cs, "")
        case UpdateOutcome.Applied(cs)     => (e, cs, "written")
        case UpdateOutcome.Failed(cs, why) => (e, cs, s"FAILED: $why")
    }
    val rows = changing.flatMap { (e, cs, note) =>
      cs.map(c =>
        s"| ${e.calibreId} | ${e.citekeys.mkString(", ")} | ${c.field.name} | ${cell(c.from)} | ${cell(c.to)} | $note |"
      )
    }
    val left = entries.collect { e =>
      e.outcome match
        case UpdateOutcome.Shared =>
          s"- ${e.calibreId}: linked from ${e.citekeys.mkString(", ")} — update one by hand (`histmersion calibre update <citekey>`)"
        case UpdateOutcome.Missing =>
          s"- ${e.calibreId}: no such Calibre entry (${e.citekeys.mkString(", ")}) — re-push, or clear the source's calibre id"
        case UpdateOutcome.Refused(why) => s"- ${e.calibreId} (${e.citekeys.mkString(", ")}): $why"
    }
    val upToDate = entries.count(_.outcome == UpdateOutcome.UpToDate)
    val head =
      if changing.isEmpty then s"_No linked Calibre entry needs updating ($upToDate up to date)._"
      else
        val verb = if applied then "updated" else "would change"
        (List(
          s"### ${changing.size} Calibre ${com.histmersion.api.Plural.noun(changing.size, "entry", "entries")} $verb · $upToDate up to date",
          "",
          "| id | citekey | field | Calibre | record | |",
          "|---:|---|---|---|---|---|"
        ) ++ rows).mkString("\n")
    val tail = if left.isEmpty then "" else s"\n\n**Left alone (${left.size})**\n\n${left.mkString("\n")}"
    val hint =
      if !applied && changing.nonEmpty then "\n\n_Dry run. Re-run with `--apply` (Calibre GUI closed)._" else ""
    head + tail + hint

  /** The fields `calibre set` may write. Formats, covers and the file itself are Calibre's business. */
  val SettableFields: List[com.histmersion.cli.Calibre.SettableFields] =
    List("title", "authors", "publisher", "pubdate", "languages", "identifiers", "tags", "comments", "series")

  /** `calibredb set_metadata` for [[Calibre.set]], or why it is refused: an unknown field, nothing to set, or a
    * blank/"Unknown" title or author (the metadata guard).
    */
  def setCmd(
      calibredb: String,
      libArgs: List[String],
      id: com.histmersion.cli.Calibre.Book.Id,
      fields: List[(com.histmersion.api.library.CollectionFieldValue.Name, com.histmersion.cli.Calibre.SetCmdFieldsPart2)]
  ): Either[String, List[String]] =
    val unknown = fields.map(_._1).filterNot(SettableFields.contains)
    def value(k: com.histmersion.api.library.CollectionFieldValue.Name) = fields.collectFirst { case (`k`, v) => v }
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

  def parseAddedId(out: String): Option[com.histmersion.cli.Calibre.Book.Id] =
    AddedIds.findFirstMatchIn(out).map(_.group(1)).flatMap(_.split(",").headOption).flatMap(_.trim.toIntOption)

  /** `calibredb list --for-machine`: authors as `A & B`, tags/formats as arrays, identifiers as an object, formats as
    * ABSOLUTE paths.
    */
  def parseMachineJson(out: String): Either[com.histmersion.cli.Calibre.Problem, List[com.histmersion.cli.Calibre.Book]] =
    if out.trim.isEmpty then Right(Nil) // no match: nothing is printed at all (#967)
    else
      io.circe.parser
        .parse(out)
        .left
        .map(e => s"calibredb returned unparseable JSON: ${e.getMessage}")
        .map(_.asArray.toList.flatten.flatMap { j =>
          val c = j.hcursor
          c.downField("id").as[Int].toOption.map { id =>
            com.histmersion.cli.Calibre.Book(
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
  def parseSqliteJson(out: String, libraryDir: com.histmersion.cli.Calibre.LibraryDir): Either[com.histmersion.cli.Calibre.Problem, List[com.histmersion.cli.Calibre.Book]] =
    // `sqlite3 -json` prints NOTHING for an empty result, not `[]` — that was "exhausted input" (#967)
    if out.trim.isEmpty then Right(Nil)
    else
      io.circe.parser
        .parse(out)
        .left
        .map(e => s"sqlite3 returned unparseable JSON: ${e.getMessage}")
        .map(_.asArray.toList.flatten.flatMap { j =>
          val c = j.hcursor
          def s(f: com.histmersion.api.library.CollectionFieldValue.Name) = c.downField(f).as[String].toOption.map(_.trim).filter(_.nonEmpty)
          c.downField("id").as[Int].toOption.map { id =>
            com.histmersion.cli.Calibre.Book(
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

  private def splitAuthors(s: com.histmersion.cli.Calibre.AuthorsText): List[com.histmersion.api.library.AuthorName] =
    s.split(" & ").map(_.trim).filter(_.nonEmpty).toList

  /** Single-quote a SQL literal for the `sqlite3` command line. The only user value that reaches SQL is a search term,
    * and it goes through here.
    */
  def sqlLiteral(s: String): String = "'" + s.replace("'", "''") + "'"

  /** The read-only projection that mirrors `calibredb --for-machine` (Calibre's schema: `books` plus link tables for
    * authors/tags/publishers/identifiers, and `data` for the files).
    */
  def sqliteQuery(where: String, limit: com.histmersion.api.Paging.Limit): String =
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
    * `calibre push`/`sync` download otherwise ([[pushFile]]).
    */
  def originalFor(originalsDir: Option[com.histmersion.cli.Calibre.OriginalsDir], citekey: com.histmersion.api.library.Citekey): Option[Path] =
    originalsDir.map(d => Path.of(d, citekey)).filter(Files.isDirectory(_)).flatMap { dir =>
      val s = Files.list(dir)
      try
        s.iterator.asScala
          .filter(p =>
            Set("pdf", "epub").contains(p.getFileName.toString.toLowerCase.split("\\.").lastOption.getOrElse(""))
          )
          .toList
          .sortBy(_.toString)
          .headOption
      finally s.close()
    }

  /** Where a push takes its file from: `--file`, else the host-side original under `cli.calibre.originals-dir`, else
    * the original the server stored (downloaded for the push).
    */
  enum PushFile:
    case Given(path: Path)
    case Local(path: Path)
    case Server

  def pushFile(explicit: Option[Path], local: => Option[Path]): PushFile =
    explicit.map(PushFile.Given(_)).orElse(local.map(PushFile.Local(_))).getOrElse(PushFile.Server)

  /** Where the file a push would add comes from, as `calibre gaps`/`sync` report it (`original`). */
  enum GapOriginal(val label: GapOriginal.Label):
    /** `<cli.calibre.originals-dir>/<citekey>/paper.*` on this host. */
    case Local extends GapOriginal("local")

    /** The original the server stores (`GET /api/sources/{ck}/original`). */
    case Stored extends GapOriginal("stored")

    /** The server did not say (an older server): counted, and the push tries. */
    case Unknown extends GapOriginal("unknown")

    /** No file anywhere — a metadata-only record (a citation-graph stub, a record with an abstract and no fetched
      * file). Nothing to push, so not a Calibre gap: it belongs on the acquire list.
      */
    case Missing extends GapOriginal("none")

  object GapOriginal:
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One source the server lists as not in Calibre, with where its original is and whether it COUNTS as a gap — the
    * `calibre gaps --quiet` reminder and `calibre sync` count only these.
    */
  final case class GapEntry(gap: CalibreGap, original: GapOriginal, counted: GapEntry.Counted)

  object GapEntry:
    type Counted = Counted.T
    object Counted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Pure: a gap counts when there is an original to push (on this host, or stored on the server) and the source is not
    * already canonical in Calibre (provenance `calibre`, or a Calibre id — the server leaves both out; an older or
    * differently-cased row is caught here too). A record with no original anywhere is listed, never counted.
    */
  def classify(gaps: List[CalibreGap], local: com.histmersion.api.library.Citekey => Option[Path]): List[GapEntry] =
    gaps.map { g =>
      val original =
        if local(g.citekey).isDefined then GapOriginal.Local
        else
          g.storedOriginal match
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

  /** `calibre gaps` as Markdown: the counted gaps as a table, then one line for the records with no fetched file (never
    * rows of the table: they are not gaps).
    */
  def markdownGaps(entries: List[GapEntry]): com.histmersion.cli.Calibre.Markdown =
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
        (List(
          s"### ${counted.size} source(s) not in Calibre",
          "",
          "| citekey | title | year | authors | provenance | original |",
          "|---|---|---|---|---|---|"
        )
          ++ rows ++ List(
            "",
            "_Push them with `histmersion calibre sync --apply`: the file is " +
              "`<cli.calibre.originals-dir>/<citekey>/paper.*` on this host (`local`), else the original the server " +
              "stored (`stored`)._"
          )).mkString("\n")
    if missing == 0 then head else s"$head\n\n${uncountedLine(missing)}"

  /** `calibre search --unlinked` (#967): the entries no source links (`sources.calibre_id`), newest first — Calibre ids
    * grow as books are added, so the highest id is the latest addition.
    */
  def unlinked(
      books: List[com.histmersion.cli.Calibre.Book],
      linked: Set[com.histmersion.cli.Calibre.LinkedCount],
      limit: com.histmersion.api.Paging.Limit
  ): List[com.histmersion.cli.Calibre.Book] =
    books.filterNot(b => linked.contains(b.id)).sortBy(-_.id).take(limit.max(1))

  /** The unlinked listing as Markdown: one line per entry with its file path(s), the way a person finds the books they
    * added that are not in the corpus yet.
    */
  def markdownUnlinked(books: List[com.histmersion.cli.Calibre.Book], total: com.histmersion.api.Paging.Page.Total): com.histmersion.cli.Calibre.Markdown =
    if books.isEmpty then "_Every Calibre entry is linked to a source._"
    else
      val lines = books.map { b =>
        val who = b.authors.take(3).mkString(", ") + (if b.authors.sizeIs > 3 then " et al." else "")
        val year = b.year.fold("")(y => s" ($y)")
        val files = if b.formats.isEmpty then "  - no file" else b.formats.map(f => s"  - `$f`").mkString("\n")
        s"- **${b.id}** · ${b.title}$year — ${if who.isEmpty then "no author" else who}\n$files"
      }
      val shown = if total > books.size then s"${books.size} of $total" else books.size.toString
      (List(
        s"### $shown Calibre ${com.histmersion.api.Plural.noun(total, "entry", "entries")} not linked to any source (newest first)",
        ""
      )
        ++ lines ++ List(
          "",
          "_Import one with `histmersion calibre import <id>`, or link it to an existing record with " +
            "`histmersion calibre link <citekey> <id>`._"
        )).mkString("\n")

  /** Render a Calibre search result as Markdown (the CLI's own output — the server never sees these). */
  def markdownSearch(books: List[com.histmersion.cli.Calibre.Book]): com.histmersion.cli.Calibre.Markdown =
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
    def titleMatches(a: com.histmersion.api.library.Title, b: com.histmersion.api.library.Title): Boolean =
      val (x, y) = (norm(a), norm(b))
      x.nonEmpty && y.nonEmpty && (x.startsWith(y) || y.startsWith(x))

    /** "Güterbock, Hans G." and "Hans G. Güterbock" are the same surname; a bracketed role or life span ("Ken Dowden
      * (ed.)", "Gimbutienė, Marija (1921-1994)") is not part of the name.
      */
    def surname(author: com.histmersion.api.library.AuthorName): com.histmersion.api.library.Source.AuthorSurname =
      val name = author.replaceAll("\\([^)]*\\)", " ").trim
      norm(if name.contains(",") then name.takeWhile(_ != ',') else name.split("\\s+").last)

    def authorsMatch(a: List[com.histmersion.api.library.AuthorName], b: List[com.histmersion.api.library.AuthorName]): Boolean =
      (a.map(surname).toSet & b.map(surname).toSet).exists(_.nonEmpty)

    /** Calibre's placeholder year for "unknown" is 101; anything before 1000 is not a real pubdate. */
    def year(b: Book): Option[com.histmersion.cli.Calibre.Book.Year] =
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

    def audit(records: List[AuditRecord], books: Map[com.histmersion.cli.Calibre.Book.Id, Book]): List[AuditFinding] =
      records.groupBy(_.calibreId).toList.sortBy(_._1).flatMap { (id, rs) =>
        val keys = rs.map(_.citekey).sorted
        books.get(id) match
          case None    => List(AuditFinding(id, keys, List(AuditIssue.Missing), None, rs.minBy(_.citekey)))
          case Some(b) =>
            val (best, found) = rs.map(r => r -> issues(r, b)).minBy((r, is) => (is.size, r.citekey))
            if found.isEmpty then Nil else List(AuditFinding(id, keys, found, Some(b), best))
      }

    private def cell(s: String): String = s.replace("|", "\\|").replace("\n", " ").take(90)

    def markdown(findings: List[AuditFinding], checked: com.histmersion.api.library.CollectionAudit.Checked): com.histmersion.cli.Calibre.Markdown =
      if findings.isEmpty then s"_All $checked linked Calibre entries agree with the corpus._"
      else
        val table =
          "| id | citekey(s) | issue | corpus | Calibre | fix |\n|---:|---|---|---|---|---|\n" +
            findings
              .map { f =>
                val corpus =
                  s"${f.record.title} — ${f.record.authors.mkString("; ")}${f.record.year.fold("")(y => s" ($y)")}"
                val cal =
                  f.calibre.fold("—")(b =>
                    s"${b.title} — ${b.authors.mkString("; ")}${year(b).fold("")(y => s" ($y)")}"
                  )
                val todo =
                  if f.fixable then s"`histmersion calibre update ${f.record.citekey}`"
                  else "re-push, or clear the source's calibre id"
                s"| ${f.calibreId} | ${f.citekeys.mkString(", ")} | ${f.issues.map(_.label).mkString("; ")} | ${cell(corpus)} | ${cell(cal)} | $todo |"
              }
              .mkString("\n")
        s"### ${findings.size} of $checked linked Calibre entries disagree with the corpus\n\n$table\n\n" +
          "_`histmersion calibre update --all` lists every change it would make; `--apply` writes them._"

  /** One book as Calibre holds it — the shape both readers below produce (`calibredb list --for-machine` and the
    * `sqlite3 -readonly` fallback normalise to this same record).
    */
  final case class Book(
      id: Book.Id,
      title: Book.Title,
      authors: List[Book.Authors],
      pubdate: Option[Book.Pubdate],
      publisher: Option[Book.Publisher],
      tags: List[Book.Tags],
      comments: Option[Book.Comments],
      identifiers: Map[Book.IdentifiersKey, Book.IdentifiersValue],
      formats: List[Book.Formats],
      /** The cover Calibre keeps beside the book (`<book dir>/cover.jpg`), absolute — `None` when the entry has none.
        * Read-only: `media covers` uploads a copy, the file stays as it is.
        */
      cover: Option[Book.Cover] = None
  ):
    /** Calibre writes `0101-01-01` for "no date"; a year below [[Acquire.MinCitekeyYear]] is that placeholder, not a
      * publication year (#714).
      */
    def year: Option[com.histmersion.cli.Calibre.Book.Year] = pubdate
      .map(_.take(4))
      .filter(_.forall(_.isDigit))
      .flatMap(_.toIntOption)
      .filter(_ >= Acquire.MinCitekeyYear)
    def doi: Option[com.histmersion.api.library.Doi] = identifiers.get("doi")
    def isbn: Option[com.histmersion.api.library.CalibreGap.Isbn] = identifiers.get("isbn")

    /** The file to import: the requested format, else PDF, else EPUB (both carry a text layer). */
    def pick(format: Option[com.histmersion.cli.Calibre.Book.Format]): Option[com.histmersion.cli.Calibre.Book.Format] =
      val wanted = format.map(_.toLowerCase).toList ++ List("pdf", "epub")
      wanted.iterator.map(ext => formats.find(_.toLowerCase.endsWith("." + ext))).collectFirst { case Some(f) =>
        f
      }

  object Book:
    type Format = Format.T
    object Format extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Year = Year.T
    object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = com.histmersion.api.library.Title
    val Title: _root_.com.histmersion.api.library.Title.type = _root_.com.histmersion.api.library.Title
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pubdate = Pubdate.T
    object Pubdate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Publisher = com.histmersion.api.library.CollectionEntry.Publisher
    val Publisher: _root_.com.histmersion.api.library.CollectionEntry.Publisher.type = _root_.com.histmersion.api.library.CollectionEntry.Publisher
    type Tags = com.histmersion.api.library.CollectionEntry.Tags
    val Tags: _root_.com.histmersion.api.library.CollectionEntry.Tags.type = _root_.com.histmersion.api.library.CollectionEntry.Tags
    type Comments = Comments.T
    object Comments extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IdentifiersKey = com.histmersion.api.library.CollectionEntry.IdentifiersKey
    val IdentifiersKey: _root_.com.histmersion.api.library.CollectionEntry.IdentifiersKey.type = _root_.com.histmersion.api.library.CollectionEntry.IdentifiersKey
    type IdentifiersValue = IdentifiersValue.T
    object IdentifiersValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Formats = com.histmersion.api.library.CollectionEntry.Formats
    val Formats: _root_.com.histmersion.api.library.CollectionEntry.Formats.type = _root_.com.histmersion.api.library.CollectionEntry.Formats
    type Cover = Cover.T
    object Cover extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** One field `calibre update` changes: what Calibre has now and what it writes. */
  final case class Change(field: Field, from: Change.From, to: Change.To)

  object Change:
    type From = com.histmersion.api.library.CollectionFieldChange.From
    val From: _root_.com.histmersion.api.library.CollectionFieldChange.From.type = _root_.com.histmersion.api.library.CollectionFieldChange.From
    type To = com.histmersion.api.library.CollectionFieldChange.To
    val To: _root_.com.histmersion.api.library.CollectionFieldChange.To.type = _root_.com.histmersion.api.library.CollectionFieldChange.To

  /** A field `calibre update` writes, by its `calibredb set_metadata --field` name. */
  enum Field(val name: Field.Name):
    case Title extends Field("title")
    case Authors extends Field("authors")
    case Pubdate extends Field("pubdate")
    case Identifiers extends Field("identifiers")
    case Tags extends Field("tags")
    case Publisher extends Field("publisher")
    case Comments extends Field("comments")

  object Field:
    type Name = com.histmersion.api.library.CollectionFieldValue.Name
    val Name: _root_.com.histmersion.api.library.CollectionFieldValue.Name.type = _root_.com.histmersion.api.library.CollectionFieldValue.Name

  /** What the `calibre … --json` commands print: case classes with derived codecs, printed through [[Output.emit]] like
    * every other `--json`, never `Json.obj` assembled by hand. The field names are the ones those commands always
    * printed, whether the server's collection answered (`of` a wire record) or this machine's library did.
    */
  object Json:

    /** `calibre search --json`: one entry. */
    final case class Book(
        id: Book.Id,
        title: Book.Title,
        authors: List[Book.Authors],
        year: Option[Book.Year],
        publisher: Option[Book.Publisher],
        tags: List[Book.Tags],
        identifiers: Map[Book.IdentifiersKey, Book.IdentifiersValue],
        formats: List[Book.Formats]
    ) derives KindlingsCodecAsObject
    object Book:
      type Id = Id.T
      object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Title = com.histmersion.api.library.Title
      val Title: _root_.com.histmersion.api.library.Title.type = _root_.com.histmersion.api.library.Title
      type Authors = Authors.T
      object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Year = com.histmersion.cli.Calibre.Book.Year
      val Year: _root_.com.histmersion.cli.Calibre.Book.Year.type = _root_.com.histmersion.cli.Calibre.Book.Year
      type Publisher = com.histmersion.api.library.CollectionEntry.Publisher
      val Publisher: _root_.com.histmersion.api.library.CollectionEntry.Publisher.type = _root_.com.histmersion.api.library.CollectionEntry.Publisher
      type Tags = com.histmersion.api.library.CollectionEntry.Tags
      val Tags: _root_.com.histmersion.api.library.CollectionEntry.Tags.type = _root_.com.histmersion.api.library.CollectionEntry.Tags
      type IdentifiersKey = com.histmersion.api.library.CollectionEntry.IdentifiersKey
      val IdentifiersKey: _root_.com.histmersion.api.library.CollectionEntry.IdentifiersKey.type = _root_.com.histmersion.api.library.CollectionEntry.IdentifiersKey
      type IdentifiersValue = IdentifiersValue.T
      object IdentifiersValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Formats = com.histmersion.api.library.CollectionEntry.Formats
      val Formats: _root_.com.histmersion.api.library.CollectionEntry.Formats.type = _root_.com.histmersion.api.library.CollectionEntry.Formats

      def of(b: Calibre.Book): Book =
        Book(b.id, b.title, b.authors, b.year, b.publisher, b.tags, b.identifiers, b.formats)
      def of(e: CollectionEntry): Book =
        Book(e.id.toIntOption.getOrElse(0), e.title, e.authors, e.year, e.publisher, e.tags, e.identifiers, e.formats)

    /** `calibre link --json`, `calibre push --json`. */
    final case class Linked(citekey: com.histmersion.api.library.Citekey, calibreId: Linked.CalibreId) derives KindlingsCodecAsObject

    object Linked:
      type CalibreId = CalibreId.T
      object CalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    /** `calibre push --replace --json`: `replaced` is the format swapped in. */
    final case class Replaced(citekey: com.histmersion.api.library.Citekey, calibreId: Replaced.CalibreId, replaced: Replaced.Value) derives KindlingsCodecAsObject

    object Replaced:
      type CalibreId = CalibreId.T
      object CalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Value = Value.T
      object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    /** `calibre update <citekey> --json`: the fields written. */
    final case class Updated(citekey: com.histmersion.api.library.Citekey, calibreId: Updated.CalibreId, changed: List[Updated.Changed]) derives KindlingsCodecAsObject

    object Updated:
      type CalibreId = CalibreId.T
      object CalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Changed = com.histmersion.api.library.CollectionUpdatedOne.Changed
      val Changed: _root_.com.histmersion.api.library.CollectionUpdatedOne.Changed.type = _root_.com.histmersion.api.library.CollectionUpdatedOne.Changed

    final case class Change(field: Change.Field, from: Change.From, to: Change.To) derives KindlingsCodecAsObject

    object Change:
      type Field = com.histmersion.api.library.CollectionFieldChange.Field
      val Field: _root_.com.histmersion.api.library.CollectionFieldChange.Field.type = _root_.com.histmersion.api.library.CollectionFieldChange.Field
      type From = com.histmersion.api.library.CollectionFieldChange.From
      val From: _root_.com.histmersion.api.library.CollectionFieldChange.From.type = _root_.com.histmersion.api.library.CollectionFieldChange.From
      type To = com.histmersion.api.library.CollectionFieldChange.To
      val To: _root_.com.histmersion.api.library.CollectionFieldChange.To.type = _root_.com.histmersion.api.library.CollectionFieldChange.To

    /** `calibre update --all --json`: one entry's outcome. */
    final case class UpdateRow(
        calibreId: UpdateRow.CalibreId,
        citekeys: List[UpdateRow.Citekeys],
        state: UpdateRow.State,
        changes: List[Change],
        reason: Option[UpdateRow.Reason]
    ) derives KindlingsCodecAsObject
    object UpdateRow:
      type CalibreId = CalibreId.T
      object CalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Citekeys = com.histmersion.api.library.CollectionUpdateRow.Citekeys
      val Citekeys: _root_.com.histmersion.api.library.CollectionUpdateRow.Citekeys.type = _root_.com.histmersion.api.library.CollectionUpdateRow.Citekeys
      type State = com.histmersion.api.library.CollectionUpdateRow.State
      val State: _root_.com.histmersion.api.library.CollectionUpdateRow.State.type = _root_.com.histmersion.api.library.CollectionUpdateRow.State
      type Reason = com.histmersion.api.library.CollectionUpdateRow.Reason
      val Reason: _root_.com.histmersion.api.library.CollectionUpdateRow.Reason.type = _root_.com.histmersion.api.library.CollectionUpdateRow.Reason

      def of(e: UpdateEntry): UpdateRow =
        val (state, changes) = e.outcome match
          case UpdateOutcome.Change(cs)    => ("would-change", cs)
          case UpdateOutcome.Applied(cs)   => ("updated", cs)
          case UpdateOutcome.Failed(cs, _) => ("failed", cs)
          case UpdateOutcome.UpToDate      => ("up-to-date", Nil)
          case UpdateOutcome.Shared        => ("shared", Nil)
          case UpdateOutcome.Missing       => ("missing", Nil)
          case UpdateOutcome.Refused(_)    => ("refused", Nil)
        val reason = e.outcome match
          case UpdateOutcome.Failed(_, r) => Some(r)
          case UpdateOutcome.Refused(r)   => Some(r)
          case _                          => None
        UpdateRow(e.calibreId, e.citekeys, state, changes.map(ch => Change(ch.field.name, ch.from, ch.to)), reason)
      def of(r: CollectionUpdateRow): UpdateRow =
        UpdateRow(
          r.id.toIntOption.getOrElse(0),
          r.citekeys,
          r.state,
          r.changes.map(ch => Change(ch.field, ch.from, ch.to)),
          r.reason
        )

    /** `calibre audit --json`: one entry that disagrees with its record; `fix` = the citekey whose record
      * `calibre update` would write over it, absent when the entry is missing.
      */
    final case class AuditRow(calibreId: AuditRow.CalibreId, citekeys: List[AuditRow.Citekeys], issues: List[AuditRow.Issues], fix: Option[AuditRow.Fix])
        derives KindlingsCodecAsObject
    object AuditRow:
      type CalibreId = CalibreId.T
      object CalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Citekeys = com.histmersion.api.library.CollectionAudit.Row.Citekeys
      val Citekeys: _root_.com.histmersion.api.library.CollectionAudit.Row.Citekeys.type = _root_.com.histmersion.api.library.CollectionAudit.Row.Citekeys
      type Issues = com.histmersion.api.library.CollectionAudit.Row.Codes
      val Issues: _root_.com.histmersion.api.library.CollectionAudit.Row.Codes.type = _root_.com.histmersion.api.library.CollectionAudit.Row.Codes
      type Fix = com.histmersion.api.library.CollectionAudit.Row.Fix
      val Fix: _root_.com.histmersion.api.library.CollectionAudit.Row.Fix.type = _root_.com.histmersion.api.library.CollectionAudit.Row.Fix

      def of(f: AuditFinding): AuditRow =
        AuditRow(f.calibreId, f.citekeys, f.issues.map(_.toString), Option.when(f.fixable)(f.record.citekey))
      def of(r: CollectionAudit.Row): AuditRow = AuditRow(r.id.toIntOption.getOrElse(0), r.citekeys, r.codes, r.fix)

    /** `calibre gaps --json` / `calibre sync --json`: EVERY source the server lists, each with where its original is
      * (`local` | `stored` | `none` | `unknown`) and whether it `counted` as a gap. `count` is the counted ones (what
      * `--quiet` reminds about), `uncounted` the rest, `total` both. The server's own fields keep their names, so a
      * script reading `gaps[].citekey` is unchanged.
      */
    final case class GapRow(
        citekey: GapRow.Citekey,
        title: GapRow.Title,
        authors: List[GapRow.Authors],
        doi: Option[GapRow.Doi],
        year: Option[GapRow.Year],
        venue: Option[GapRow.Venue],
        summary: Option[GapRow.Summary],
        topics: List[GapRow.Topics],
        provenance: Option[GapRow.Provenance],
        calibreId: Option[GapRow.CalibreId],
        isbn: Option[GapRow.Isbn],
        storedOriginal: Option[GapRow.StoredOriginal],
        original: GapRow.Original,
        counted: GapRow.Counted
    ) derives KindlingsCodecAsObject
    object GapRow:
      type Citekey = com.histmersion.api.library.Citekey
      val Citekey: _root_.com.histmersion.api.library.Citekey.type = _root_.com.histmersion.api.library.Citekey
      type Title = com.histmersion.api.library.Title
      val Title: _root_.com.histmersion.api.library.Title.type = _root_.com.histmersion.api.library.Title
      type Authors = com.histmersion.api.library.CalibreGap.Authors
      val Authors: _root_.com.histmersion.api.library.CalibreGap.Authors.type = _root_.com.histmersion.api.library.CalibreGap.Authors
      type Doi = com.histmersion.api.library.Doi
      val Doi: _root_.com.histmersion.api.library.Doi.type = _root_.com.histmersion.api.library.Doi
      type Year = com.histmersion.cli.Calibre.Book.Year
      val Year: _root_.com.histmersion.cli.Calibre.Book.Year.type = _root_.com.histmersion.cli.Calibre.Book.Year
      type Venue = com.histmersion.api.library.Source.Venue
      val Venue: _root_.com.histmersion.api.library.Source.Venue.type = _root_.com.histmersion.api.library.Source.Venue
      type Summary = com.histmersion.api.library.CalibreGap.Summary
      val Summary: _root_.com.histmersion.api.library.CalibreGap.Summary.type = _root_.com.histmersion.api.library.CalibreGap.Summary
      type Topics = com.histmersion.api.library.CalibreGap.Topics
      val Topics: _root_.com.histmersion.api.library.CalibreGap.Topics.type = _root_.com.histmersion.api.library.CalibreGap.Topics
      type Provenance = com.histmersion.api.library.CalibreGap.Provenance
      val Provenance: _root_.com.histmersion.api.library.CalibreGap.Provenance.type = _root_.com.histmersion.api.library.CalibreGap.Provenance
      type CalibreId = com.histmersion.api.library.Source.CalibreId
      val CalibreId: _root_.com.histmersion.api.library.Source.CalibreId.type = _root_.com.histmersion.api.library.Source.CalibreId
      type Isbn = com.histmersion.api.library.CalibreGap.Isbn
      val Isbn: _root_.com.histmersion.api.library.CalibreGap.Isbn.type = _root_.com.histmersion.api.library.CalibreGap.Isbn
      type StoredOriginal = com.histmersion.api.library.CalibreGap.StoredOriginal
      val StoredOriginal: _root_.com.histmersion.api.library.CalibreGap.StoredOriginal.type = _root_.com.histmersion.api.library.CalibreGap.StoredOriginal
      type Original = Original.T
      object Original extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Counted = Counted.T
      object Counted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

      def of(e: Calibre.GapEntry): GapRow =
        val g = e.gap
        GapRow(
          g.citekey,
          g.title,
          g.authors,
          g.doi,
          g.year,
          g.venue,
          g.summary,
          g.topics,
          g.provenance,
          g.calibreId,
          g.isbn,
          g.storedOriginal,
          e.original.label,
          e.counted
        )
      def of(r: CollectionGapRow): GapRow =
        GapRow(
          r.citekey,
          r.title,
          r.authors,
          r.doi,
          r.year,
          r.venue,
          r.summary,
          r.topics,
          r.provenance,
          r.calibreId,
          r.isbn,
          r.storedOriginal,
          r.original,
          r.counted
        )

    final case class Gaps(count: Gaps.Count, uncounted: Gaps.Uncounted, total: Gaps.Total, gaps: List[GapRow]) derives KindlingsCodecAsObject
    object Gaps:
      type Count = com.histmersion.api.library.CalibreGaps.Count
      val Count: _root_.com.histmersion.api.library.CalibreGaps.Count.type = _root_.com.histmersion.api.library.CalibreGaps.Count
      type Uncounted = com.histmersion.api.library.CollectionGaps.Uncounted
      val Uncounted: _root_.com.histmersion.api.library.CollectionGaps.Uncounted.type = _root_.com.histmersion.api.library.CollectionGaps.Uncounted
      type Total = com.histmersion.api.library.CollectionGaps.Total
      val Total: _root_.com.histmersion.api.library.CollectionGaps.Total.type = _root_.com.histmersion.api.library.CollectionGaps.Total

      def of(entries: List[Calibre.GapEntry]): Gaps =
        val n = entries.count(_.counted)
        Gaps(n, entries.size - n, entries.size, entries.map(GapRow.of))
      def of(g: CollectionGaps): Gaps = Gaps(g.count, g.uncounted, g.total, g.gaps.map(GapRow.of))

  /** What `calibre push` sends into Calibre for one source. */
  final case class Push(
      title: Push.Title,
      authors: List[Push.Authors],
      doi: Option[Push.Doi] = None,
      isbn: Option[Push.Isbn] = None,
      year: Option[Push.Year] = None,
      publisher: Option[Push.Publisher] = None,
      summary: Option[Push.Summary] = None,
      tags: List[Push.Tags] = Nil
  )

  object Push:
    type Replace = Replace.T
    object Replace extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = com.histmersion.api.library.Title
    val Title: _root_.com.histmersion.api.library.Title.type = _root_.com.histmersion.api.library.Title
    type Authors = com.histmersion.api.library.CalibreGap.Authors
    val Authors: _root_.com.histmersion.api.library.CalibreGap.Authors.type = _root_.com.histmersion.api.library.CalibreGap.Authors
    type Doi = com.histmersion.api.library.Doi
    val Doi: _root_.com.histmersion.api.library.Doi.type = _root_.com.histmersion.api.library.Doi
    type Isbn = com.histmersion.api.library.CalibreGap.Isbn
    val Isbn: _root_.com.histmersion.api.library.CalibreGap.Isbn.type = _root_.com.histmersion.api.library.CalibreGap.Isbn
    type Year = com.histmersion.cli.Calibre.Book.Year
    val Year: _root_.com.histmersion.cli.Calibre.Book.Year.type = _root_.com.histmersion.cli.Calibre.Book.Year
    type Publisher = com.histmersion.api.library.CollectionEntry.Publisher
    val Publisher: _root_.com.histmersion.api.library.CollectionEntry.Publisher.type = _root_.com.histmersion.api.library.CollectionEntry.Publisher
    type Summary = com.histmersion.api.library.Source.Summary
    val Summary: _root_.com.histmersion.api.library.Source.Summary.type = _root_.com.histmersion.api.library.Source.Summary
    type Tags = com.histmersion.api.library.CollectionEntry.Tags
    val Tags: _root_.com.histmersion.api.library.CollectionEntry.Tags.type = _root_.com.histmersion.api.library.CollectionEntry.Tags
