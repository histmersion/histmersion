package com.histmersion
package cli
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

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
import com.histmersion.util.json.JsonCodec
import java.text.Normalizer
import com.histmersion.api.Api
import com.histmersion.api.Paging
import com.histmersion.api.library.CollectionSync
import com.histmersion.api.library.CollectionFieldValue
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Source
import com.histmersion.api.library.CollectionUpdateAll
import com.histmersion.api.library.CollectionFieldChange
import com.histmersion.api.library.CollectionUpdatedOne
import com.histmersion.lint.{tolerated, LintRule}

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

  private def libArgs = Shell.command("--with-library", cfg.library)

  /** The Calibre GUI holds an exclusive lock on the library for writes. */
  def guiRunning: Boolean = shell.isRunning(Shell.ProcessMatch("/Applications/calibre.app/Contents/MacOS/calibre"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def metadataDb: Path = Path.of(cfg.library.value, "metadata.db")

  def libraryExists: Boolean = Files.isRegularFile(metadataDb)

  // ── reading ─────────────────────────────────────────────────────────────────────────────────

  def search(
      query: Api.Search.Text,
      limit: Paging.Limit = Paging.Limit(20)
  ): Either[Problem, List[Calibre.Book]] =
    viaCalibredb(Some(query), limit).orElse(viaSqlite(Some(query), None, limit))

  /** The whole library (read-only), for the audit: one listing instead of a call per entry. */
  def all(limit: Paging.Limit = Paging.Limit(1000000)): Either[Problem, List[Calibre.Book]] =
    viaCalibredb(None, limit).orElse(viaSqlite(None, None, limit))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def byId(id: Calibre.Book.Id): Either[Problem, Option[Calibre.Book]] =
    viaCalibredb(Some(Api.Search.Text(shown"id:$id")), Paging.Limit(1))
      .map(_.find(_.id == id))
      .orElse(viaSqlite(None, Some(id), Paging.Limit(1)).map(_.headOption))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def viaCalibredb(
      query: Option[Api.Search.Text],
      limit: Paging.Limit
  ): Either[Problem, List[Calibre.Book]] =
    val cmd = Shell.command(cfg.calibredb, "list") ++ libArgs ++
      Shell.command(
        "--for-machine",
        "--fields",
        "id,title,authors,pubdate,publisher,tags,comments,identifiers,formats,cover",
        "--limit",
        limit
      ) ++ query.toList.flatMap(q => Shell.command("--search", q))
    shell.run(cmd).flatMap(Calibre.parseMachineJson)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def viaSqlite(
      query: Option[Api.Search.Text],
      id: Option[Calibre.Book.Id],
      limit: Paging.Limit
  ): Either[Problem, List[Calibre.Book]] =
    val where = id match
      case Some(n) => shown"b.id = $n"
      case None    =>
        val like = Calibre.sqlLiteral("%" + query.fold("")(_.value) + "%")
        s"(b.title LIKE $like OR b.author_sort LIKE $like)"
    val sql = Calibre.sqliteQuery(where, limit)
    shell
      .run(Shell.command(cfg.sqlite, "-readonly", "-json", metadataDb, sql))
      .flatMap(Calibre.parseSqliteJson(_, cfg.library))

  // ── writing (push) ──────────────────────────────────────────────────────────────────────────

  /** `calibredb add` + `set_metadata` — the port of `to_calibre.py`. Returns the NEW Calibre id.
    *
    * The metadata guard is the point: Calibre entries with an empty or "Unknown" title/author are unfindable junk, and
    * the whole reason this pipeline pushes metadata at all is that Calibre's own metadata sources key on ISBN and
    * cannot identify a journal article.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def add(file: Path, meta: Calibre.Push): Either[Problem, Calibre.Book.Id] =
    for
      _ <- Calibre.guard(meta.title, meta.authors)
      _ <- Either.cond(
        !guiRunning,
        (),
        Problem.Unclassified(
          Legacy.Text("the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)")
        )
      )
      _ <- Either.cond(Files.isRegularFile(file), (), Problem.Unclassified(Legacy.Text(s"no such file: $file")))
      out <- shell.run(addCmd(file, meta))
      id <- Calibre
        .parseAddedId(out)
        .toRight(Problem.Unclassified(Legacy.Text(s"calibredb add did not report a new id: ${out.trim.take(200)}")))
      _ <- setMetadata(id, meta)
    yield id

  /** Bring an EXISTING entry in line with the corpus record (#831) — for when a record's title or authors change after
    * the push (a chapter split out of a volume left the volume's entry titled as the chapter), and to leave the entry
    * complete: identifiers merged, tags = the record's topics, the record's year. What is written is
    * [[Calibre.updatePlan]]; nothing is run when the entry already agrees. Returns what changed.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def update(
      id: Calibre.Book.Id,
      meta: Calibre.Push
  ): Either[Problem, List[Calibre.Change]] =
    for
      _ <- Calibre.guard(meta.title, Calibre.normaliseAuthors(meta.authors))
      _ <- Either.cond(!guiRunning, (), GuiRunning)
      cur <- byId(id).flatMap(_.toRight(Problem.Unclassified(Legacy.Text(shown"there is no Calibre entry $id"))))
      changes <- Calibre.updatePlan(meta, cur)
      _ <- write(id, changes)
    yield changes

  /** `calibre update --all` (#831): plan the update of every linked entry against ONE listing of the library; with
    * `apply`, write the entries that would change. An entry linked from more than one record (a chapter and its volume)
    * is left alone — which record it should show is a decision for a person, `calibre update <citekey>`.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def updateAll(
      linked: List[LinkedRecord],
      apply: CollectionSync.Request.Apply
  ): Either[Problem, List[UpdateEntry]] =
    for
      _ <- Either.cond(!apply.value || !guiRunning, (), GuiRunning)
      books <- all()
    yield Calibre.planAll(linked, books.map(b => b.id -> b).toMap).map { e =>
      e.outcome match
        case UpdateOutcome.Change(cs) if apply.value =>
          e.modify(_.outcome)
            .setTo(
              write(e.calibreId, cs)
                .fold(
                  p => UpdateOutcome.Failed(cs, p),
                  _ => UpdateOutcome.Applied(cs)
                )
            )
        case _ => e
    }

  /** One `set_metadata` with the planned fields; nothing at all when there are none. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private def write(id: Calibre.Book.Id, changes: List[Calibre.Change]): Either[Problem, Unit] =
    if changes.isEmpty then Right(())
    else shell.run(Calibre.setMetadataCmd(cfg.calibredb, libArgs, id, changes)).map(_ => ())

  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private val GuiRunning =
    Problem.Unclassified(
      Legacy.Text("the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)")
    )

  /** Set named fields on an existing entry — the explicit, reviewed counterpart of [[update]] (`enrich-calibre` applies
    * approved fixes with it). Same guards as every other write.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def set(id: Calibre.Book.Id, fields: List[CollectionFieldValue]): Either[Problem, Unit] =
    for
      cmd <- Calibre.setCmd(cfg.calibredb, libArgs, id, fields)
      _ <- Either.cond(
        !guiRunning,
        (),
        GuiRunning
      )
      _ <- byId(id).flatMap(_.toRight(Problem.Unclassified(Legacy.Text(shown"there is no Calibre entry $id"))))
      _ <- shell.run(cmd)
    yield ()

  /** Replace the file of an EXISTING entry (#652): `calibredb add_format`, which replaces the entry's format of the
    * same type. Unlike [[add]] it never creates an entry, so the link stays valid.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def replaceFormat(id: Calibre.Book.Id, file: Path): Either[Problem, Calibre.Book.Format] =
    for
      fmt <- Calibre.formatOf(file)
      _ <- Either.cond(
        !guiRunning,
        (),
        GuiRunning
      )
      _ <- Either.cond(Files.isRegularFile(file), (), Problem.Unclassified(Legacy.Text(s"no such file: $file")))
      _ <- byId(id).flatMap(_.toRight(Problem.Unclassified(Legacy.Text(shown"there is no Calibre entry $id"))))
      _ <- shell.run(Calibre.replaceFormatCmd(cfg.calibredb, libArgs, id, file))
    yield fmt

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def addCmd(file: Path, meta: Calibre.Push): List[String] =
    Shell.command(cfg.calibredb, "add", file) ++ libArgs ++
      Shell.command("--title", meta.title.trim, "--authors", meta.authors.mkString(" & "), "--duplicates") ++
      meta.doi.toList.flatMap(d => List("--identifier", shown"doi:$d")) ++
      meta.isbn.toList.flatMap(i => Shell.command("--isbn", i)) ++
      (if meta.tags.isEmpty then Nil else List("--tags", meta.tags.mkString(",")))

  /** The fields `calibredb add` cannot set. Best-effort: a failure here leaves a usable entry. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def setMetadata(id: Calibre.Book.Id, meta: Calibre.Push): Either[Problem, Unit] =
    val fields = List(
      meta.publisher.map(p => shown"publisher:$p"),
      meta.year.map(y => s"pubdate:$y-01-01"),
      meta.summary.map(c => shown"comments:$c")
    ).flatten
    if fields.isEmpty then Right(())
    else
      val cmd = Shell.command(cfg.calibredb, "set_metadata") ++ libArgs ++ Shell.command(id) ++
        fields.flatMap(f => List("--field", f))
      shell
        .run(cmd)
        .map(_ => ())
        .left
        .map(e => Problem.Unclassified(Legacy.Text(s"added as $id but metadata was not set: ${e.sentence}")))

/** The PURE half: parsing what the two readers print, the metadata guard, and SQL assembly. */
object Calibre:
  type VolumeNumber = VolumeNumber.T
  object VolumeNumber extends Newtype[Int, Pure]:
    given Display[T] = Display(number => s"${number.value}")

  /** Values that mean "nobody filled this in". Pushing one of these into Calibre creates an entry nobody will ever find
    * again, so both `import` and `push` refuse.
    */

  private val Blank = Set("", "unknown", "unknown author", "untitled", "n/a", "?", "none")

  def isBlank(s: String): Boolean = Blank.contains(Option(s).getOrElse("").trim.toLowerCase)

  /** The metadata guard, shared by `import` (Calibre → corpus) and `push` (corpus → Calibre). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def guard(title: api.library.Title, authors: List[AuthorName]): Either[Problem, Unit] =
    val missing = List(
      Option.when(isBlank(title.value))("title"),
      Option.when(authors.forall(a => isBlank(a.value)))("author")
    ).flatten
    Either.cond(
      missing.isEmpty,
      (),
      Problem.Unclassified(
        Legacy.Text(
          s"empty or Unknown ${missing.mkString(" and ")} — fix the metadata first; " +
            "an untitled / Unknown-Author entry is unfindable and must not be created"
        )
      )
    )

  // ── import: citekeys and where they land ─────────────────────────────────────────────────────

  private val Volume = """(?i)\b(?:volume\s*|vol\.\s*|vol\s+)([0-9]+|[ivxlc]+)\b""".r

  /** The volume a title names — `Volume I`, `Vol. 2`, `volume 3` — so two volumes of one work get two citekeys.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def volumeOf(title: api.library.Title): Option[VolumeNumber] =
    Volume
      .findFirstMatchIn(title.value)
      .flatMap(m => m.group(1).toIntOption.orElse(roman(m.group(1))))
      .filter(_ > 0)
      .map(VolumeNumber(_))

  private def roman(s: String): Option[Int] =
    val v = Map('i' -> 1, 'v' -> 5, 'x' -> 10, 'l' -> 50, 'c' -> 100)
    val ds = s.toLowerCase.toList.map(v)
    Option.when(ds.nonEmpty)(ds.zipAll(ds.drop(1), 0, 0).map((a, b) => if a < b then -a else a).sum)

  /** The citekey `calibre import` generates: the corpus convention ([[Acquire.citekey]]) plus `_v<n>` for a numbered
    * volume.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def citekeyFor(b: Book): api.library.Citekey =
    // `López-Ruiz` gives `lopezruiz`, not `lpezruiz` (#830): Acquire.citekey folds accents itself (#878)
    api.library.Citekey(
      Acquire.citekey(b.authors, b.year, Some(b.title)).value +
        volumeOf(b.title).fold("")(n => shown"_v$n")
    )

  /** Whether importing Calibre entry `id` under `ck` may go ahead (#714: two volumes once generated the same citekey
    * and were silently merged into one record).
    *
    * @param explicit
    *   the user passed `--citekey`, so landing on an existing record is deliberate
    * @param existing
    *   `None` when no record has `ck`; otherwise that record's Calibre id
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def importTarget(
      ck: api.library.Citekey,
      id: Book.Id,
      explicit: ImportTarget.Explicit,
      existing: Option[Option[Source.CalibreId]]
  ): Either[Problem, ImportTarget] =
    existing match
      case None                                                    => Right(ImportTarget.Create)
      case Some(Some(linked)) if linked.value == id.value.toString => Right(ImportTarget.Update(None))
      case Some(linked) if explicit.asked                          =>
        Right(ImportTarget.Update(linked.map(l => ImportTarget.Update.Previous(l.value))))
      case Some(linked) =>
        val what = linked.fold("a record that is not linked to Calibre")(l => shown"the record of Calibre id $l")
        Left(
          Problem.Unclassified(
            Legacy.Text(
              shown"the generated citekey '$ck' is already $what — importing Calibre id $id would merge two " +
                shown"works into one record. Pass --citekey <another key> for a separate record, or --citekey $ck " +
                "to import into that record on purpose"
            )
          )
        )

  /** The summary an import writes (#896): Calibre's description only fills an empty one — a re-import into an existing
    * record never replaces a summary an agent compiled with the (often truncated) Calibre comments. `None` leaves the
    * record's summary alone.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def importSummary(fromCalibre: Option[Source.Summary], current: Option[Source.Summary]): Option[Source.Summary] =
    if current.exists(_.value.trim.nonEmpty) then None else fromCalibre

  /** The formats `calibre push --replace` puts into an entry: the two the corpus imports. */
  val ReplaceableFormats: List[Book.Format] = List(Book.Format("pdf"), Book.Format("epub"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def formatOf(file: Path): Either[Problem, Book.Format] =
    val ext = file.getFileName.toString.reverse.takeWhile(_ != '.').reverse.toLowerCase
    Either.cond(
      ReplaceableFormats.contains(Book.Format(ext)),
      Book.Format(ext),
      Problem.Unclassified(Legacy.Text(s"'${file.getFileName}' is not a ${ReplaceableFormats.mkString(" or ")} file"))
    )

  /** `calibredb add_format` replaces the entry's existing format of the same type (that is its default;
    * `--dont-replace` would be the opposite).
    */
  def replaceFormatCmd(
      calibredb: CalibreCliConfig.Calibredb,
      libArgs: List[String],
      id: Book.Id,
      file: Path
  ): List[String] =
    Shell.command(calibredb, "add_format") ++ libArgs ++ Shell.command(id, file)

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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def updatePlan(
      meta: Push,
      current: Book
  ): Either[Problem, List[Change]] =
    val authors = normaliseAuthors(meta.authors)
    guard(meta.title, authors).map { _ =>
      def empty(v: Option[String]) = v.forall(s => isBlank(s) || s.trim == "-")
      def change(f: Field, from: String, to: String) =
        Option.when(from != to)(Change(f, Change.From(from), Change.To(to)))
      val ids = mergeIdentifiers(current.identifiers, meta.doi, meta.isbn)
      List(
        // compared and written trimmed (#883): a stray space is not a different title
        Option.when(current.title.trim != meta.title.trim)(
          Change(
            Field.Title,
            Change.From(current.title.value),
            Change.To(meta.title.value.trim)
          )
        ),
        change(Field.Authors, current.authors.mkString(" & "), authors.mkString(" & ")),
        meta.year.flatMap(y =>
          Option.when(Audit.year(current) != Some(y))(
            Change(
              Field.Pubdate,
              Change.From(Audit.year(current).fold("")(_.toString)),
              Change.To(s"$y-01-01")
            )
          )
        ),
        Option.when(ids != current.identifiers)(
          Change(
            Field.Identifiers,
            Change.From(identifiersValue(current.identifiers)),
            Change.To(identifiersValue(ids))
          )
        ),
        Option.when(
          meta.tags.nonEmpty && meta.tags.distinct.sorted != current.tags.distinct.sorted
        )(
          Change(
            Field.Tags,
            Change.From(current.tags.mkString(",")),
            Change.To(meta.tags.distinct.mkString(","))
          )
        ),
        meta.publisher
          .map(_.value)
          .filter(p => empty(current.publisher.map(_.value)) && !isBlank(p))
          .map(p =>
            Change(
              Field.Publisher,
              Change.From(current.publisher.fold("")(_.value)),
              Change.To(p)
            )
          ),
        meta.summary
          .map(_.value)
          .filter(d => empty(current.comments.map(_.value)) && !isBlank(d))
          .map(d =>
            Change(
              Field.Comments,
              Change.From(current.comments.fold("")(_.value)),
              Change.To(d)
            )
          )
      ).flatten
    }

  /** `calibredb set_metadata` writing `changes` into entry `id`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def setMetadataCmd(
      calibredb: CalibreCliConfig.Calibredb,
      libArgs: List[String],
      id: Book.Id,
      changes: List[Change]
  ): List[String] =
    Shell.command(calibredb, "set_metadata") ++ libArgs ++ Shell.command(id) ++
      changes.flatMap(c => List("--field", shown"${c.field.name}:${c.to}"))

  /** Calibre's identifiers with the record's merged in: the DOI is the record's (it names the work the corpus cites),
    * an ISBN only fills a gap, and nothing Calibre has is ever dropped. A DOI is case-insensitive, so one differing
    * only in case is left as Calibre has it (#883). A value with a comma cannot cross `--field identifiers:` (the comma
    * separates the pairs), so it is left out.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mergeIdentifiers(
      current: Map[Book.IdentifiersKey, Book.IdentifiersValue],
      doi: Option[api.library.Doi],
      isbn: Option[CalibreGap.Isbn]
  ): Map[Book.IdentifiersKey, Book.IdentifiersValue] =
    def ok(v: String) = v.trim.nonEmpty && !v.contains(",")
    val sameDoi = (d: String) => current.get(Book.IdentifiersKey("doi")).exists(_.value.trim.equalsIgnoreCase(d))
    current ++ doi
      .map(_.value.trim)
      .filter(ok)
      .filterNot(sameDoi)
      .map(d => Book.IdentifiersKey("doi") -> Book.IdentifiersValue(d)) ++
      isbn
        .map(_.value.trim)
        .filter(ok)
        .filterNot(_ => current.get(Book.IdentifiersKey("isbn")).exists(_.trim.nonEmpty))
        .map(i => Book.IdentifiersKey("isbn") -> Book.IdentifiersValue(i))

  /** `type:value,type:value`, as `--field identifiers:` takes it, in a stable order. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def identifiersValue(ids: Map[Book.IdentifiersKey, Book.IdentifiersValue]): String =
    ids.toList.sortBy(_._1).map((k, v) => shown"$k:$v").mkString(",")

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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def normaliseAuthor(raw: AuthorName): AuthorName =
    val unbracketed = raw.value.replaceAll("\\([^)]*\\)|\\[[^\\]]*\\]", " ")
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
    AuthorName(ordered.replaceAll("\\s+", " ").trim)

  /** [[normaliseAuthor]] over a list, dropping names that end up empty and repeats. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def normaliseAuthors(authors: List[AuthorName]): List[AuthorName] =
    authors.map(normaliseAuthor).filter(_.value.nonEmpty).distinct

  /** Plan `update --all`: one [[UpdateEntry]] per linked Calibre id, in id order. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def planAll(linked: List[LinkedRecord], books: Map[Book.Id, Book]): List[UpdateEntry] =
    linked.groupBy(_.calibreId.value).toList.sortBy(kv => kv._1: Int).map { (idValue, rs) =>
      val id = Book.Id(idValue)
      val keys = rs.map(_.citekey.value).distinct.sorted
      val outcome =
        if keys.sizeIs > 1 then UpdateOutcome.Shared
        else
          books.get(id) match
            case None    => UpdateOutcome.Missing
            case Some(b) =>
              updatePlan(rs.head.meta, b).fold(
                p => UpdateOutcome.Refused(p),
                cs => if cs.isEmpty then UpdateOutcome.UpToDate else UpdateOutcome.Change(cs)
              )
      UpdateEntry(id, keys.map(UpdateEntry.Citekeys(_)), outcome)
    }

  /** `update --all` as Markdown: what changes (or changed), then what was left alone and why. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdownUpdateAll(entries: List[UpdateEntry], applied: CollectionUpdateAll.Applied): String =
    def cell[A](value: A)(using display: Display[A]) =
      display.text(value).replace("|", "\\|").replace("\n", " ").take(80)
    val changing = entries.collect { e =>
      e.outcome match
        case UpdateOutcome.Change(cs)      => (e, cs, "")
        case UpdateOutcome.Applied(cs)     => (e, cs, "written")
        case UpdateOutcome.Failed(cs, why) => (e, cs, s"FAILED: ${why.sentence}")
    }
    val rows = changing.flatMap { (e, cs, note) =>
      cs.map(c =>
        shown"| ${e.calibreId} | ${e.citekeys.mkString(", ")} | ${c.field.name} | ${cell(c.from)} | ${cell(c.to)} | $note |"
      )
    }
    val left = entries.collect { e =>
      e.outcome match
        case UpdateOutcome.Shared =>
          shown"- ${e.calibreId}: linked from ${e.citekeys.mkString(", ")} — update one by hand (`histmersion calibre update <citekey>`)"
        case UpdateOutcome.Missing =>
          shown"- ${e.calibreId}: no such Calibre entry (${e.citekeys.mkString(", ")}) — re-push, or clear the source's calibre id"
        case UpdateOutcome.Refused(why) => shown"- ${e.calibreId} (${e.citekeys.mkString(", ")}): ${why.sentence}"
    }
    val upToDate = entries.count(_.outcome == UpdateOutcome.UpToDate)
    val head =
      if changing.isEmpty then s"_No linked Calibre entry needs updating ($upToDate up to date)._"
      else
        val verb = if applied.value then "updated" else "would change"
        (List(
          shown"### ${changing.size} Calibre ${Plural.noun(Plural.Count(changing.size.toLong), Plural.Noun.Singular("entry"), Plural.Noun.Plural("entries"))} $verb · $upToDate up to date",
          "",
          "| id | citekey | field | Calibre | record | |",
          "|---:|---|---|---|---|---|"
        ) ++ rows).mkString("\n")
    val tail = if left.isEmpty then "" else s"\n\n**Left alone (${left.size})**\n\n${left.mkString("\n")}"
    val hint =
      if !applied.value && changing.nonEmpty then "\n\n_Dry run. Re-run with `--apply` (Calibre GUI closed)._" else ""
    head + tail + hint

  /** The fields `calibre set` may write. Formats, covers and the file itself are Calibre's business. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val SettableFields: List[CollectionFieldValue.Name] =
    List("title", "authors", "publisher", "pubdate", "languages", "identifiers", "tags", "comments", "series")
      .map(CollectionFieldValue.Name(_))

  /** `calibredb set_metadata` for [[Calibre.set]], or why it is refused: an unknown field, nothing to set, or a
    * blank/"Unknown" title or author (the metadata guard).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def setCmd(
      calibredb: CalibreCliConfig.Calibredb,
      libArgs: List[String],
      id: Book.Id,
      fields: List[CollectionFieldValue]
  ): Either[Problem, List[String]] =
    val unknown = fields.map(_.name).filterNot(SettableFields.contains)
    def value(k: String) = fields.collectFirst { case f if f.name.value == k => f.value.value }
    for
      _ <- Either.cond(
        fields.nonEmpty,
        (),
        Problem.Unclassified(Legacy.Text("nothing to set — pass --field <name>:<value>"))
      )
      _ <- Either.cond(
        unknown.isEmpty,
        (),
        Problem.Unclassified(
          Legacy.Text(s"cannot set ${unknown.mkString(", ")} — settable: ${SettableFields.mkString(", ")}")
        )
      )
      _ <- value("title").fold(Right(()))(t =>
        Either.cond(!isBlank(t), (), Problem.Unclassified(Legacy.Text("refusing a blank or Unknown title")))
      )
      _ <- value("authors").fold(Right(()))(a =>
        Either
          .cond(
            a.split("&").map(_.trim).forall(x => !isBlank(x)),
            (),
            Problem.Unclassified(Legacy.Text("refusing a blank or Unknown author"))
          )
      )
    yield Shell.command(calibredb, "set_metadata") ++ libArgs ++ Shell.command(id) ++
      fields.flatMap(f => List("--field", shown"${f.name}:${f.value}"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseAddedId(out: String): Option[Book.Id] =
    AddedIds
      .findFirstMatchIn(out)
      .map(_.group(1))
      .flatMap(_.split(",").headOption)
      .flatMap(_.trim.toIntOption)
      .map(Book.Id(_))

  /** `calibredb list --for-machine`: authors as `A & B`, tags/formats as arrays, identifiers as an object, formats as
    * ABSOLUTE paths.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def parseMachineJson(out: String): Either[Problem, List[Book]] =
    if out.trim.isEmpty then Right(Nil) // no match: nothing is printed at all (#967)
    else
      RawJson
        .parse(out)
        .left
        .map(e => Problem.Unclassified(Legacy.Text(s"calibredb returned unparseable JSON: $e")))
        .map(_.asArray.toList.flatten.flatMap { j =>
          val c = j.cursor
          c.downField("id").as[Book.Id].toOption.map { id =>
            Book(
              id = id,
              title = c.downField("title").as[Book.Title].getOrElse(Book.Title("")),
              authors = splitAuthors(c.downField("authors").as[String].getOrElse("")),
              pubdate = c.downField("pubdate").as[Book.Pubdate].toOption,
              publisher = c.downField("publisher").as[Book.Publisher].toOption.filter(_.nonEmpty),
              tags = c.downField("tags").as[List[Book.Tags]].getOrElse(Nil),
              comments = c.downField("comments").as[Book.Comments].toOption.filter(_.nonEmpty),
              identifiers = c
                .downField("identifiers")
                .as[Map[Book.IdentifiersKey, Book.IdentifiersValue]]
                .getOrElse(Map.empty),
              formats = c.downField("formats").as[List[Book.Formats]].getOrElse(Nil),
              cover = c.downField("cover").as[Book.Cover].toOption.map(_.trim).filter(_.nonEmpty)
            )
          }
        })

  /** `sqlite3 -json`: every column is a scalar, the multi-valued ones joined by [[sqliteQuery]], and `formats` relative
    * to the library root.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def parseSqliteJson(out: String, libraryDir: CalibreCliConfig.LibraryDir): Either[Problem, List[Book]] =
    // `sqlite3 -json` prints NOTHING for an empty result, not `[]` — that was "exhausted input" (#967)
    if out.trim.isEmpty then Right(Nil)
    else
      RawJson
        .parse(out)
        .left
        .map(e => Problem.Unclassified(Legacy.Text(s"sqlite3 returned unparseable JSON: $e")))
        .map(_.asArray.toList.flatten.flatMap { j =>
          val c = j.cursor
          val root = Path.of(libraryDir.value)
          def s(f: String) = c.downField(f).as[String].toOption.map(_.trim).filter(_.nonEmpty)
          c.downField("id").as[Book.Id].toOption.map { id =>
            Book(
              id = id,
              title = Book.Title(s("title").getOrElse("")),
              authors = splitAuthors(s("authors").getOrElse("")),
              pubdate = s("pubdate").map(Book.Pubdate(_)),
              publisher = s("publisher").map(Book.Publisher(_)),
              tags = s("tags").toList.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty).map(Book.Tags(_)),
              comments = s("comments").map(Book.Comments(_)),
              identifiers = s("identifiers").toList
                .flatMap(_.split(","))
                .flatMap { p =>
                  p.split(":", 2) match
                    case Array(k, v) => Some(Book.IdentifiersKey(k.trim) -> Book.IdentifiersValue(v.trim))
                    case _           => None
                }
                .toMap,
              formats = s("formats").toList
                .flatMap(_.split("\\|"))
                .map(_.trim)
                .filter(_.nonEmpty)
                .map(f => Book.Formats(root.resolve(f).toString)),
              cover = s("cover").map(f => Book.Cover(root.resolve(f).toString))
            )
          }
        })

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def splitAuthors(s: String): List[AuthorName] =
    s.split(" & ").map(_.trim).filter(_.nonEmpty).toList.map(AuthorName(_))

  /** Single-quote a SQL literal for the `sqlite3` command line. The only user value that reaches SQL is a search term,
    * and it goes through here.
    */
  def sqlLiteral(s: String): String = "'" + s.replace("'", "''") + "'"

  /** The read-only projection that mirrors `calibredb --for-machine` (Calibre's schema: `books` plus link tables for
    * authors/tags/publishers/identifiers, and `data` for the files).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sqliteQuery(where: String, limit: Paging.Limit): String =
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
       |FROM books b WHERE $where ORDER BY b.id DESC LIMIT ${limit.value.max(1)}""".stripMargin
      .replace("\n", " ")

  /** The host-side original of a source, when a directory of fetched files is configured:
    * `<originals>/<citekey>/paper.*` — an optional override of the original the server stored, which
    * `calibre push`/`sync` download otherwise ([[pushFile]]).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def originalFor(
      originalsDir: Option[CalibreCliConfig.OriginalsDir],
      citekey: api.library.Citekey
  ): Option[Path] =
    originalsDir.map(d => Path.of(d.value, citekey.value)).filter(Files.isDirectory(_)).flatMap { dir =>
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
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  enum PushFile:
    case Given(path: Path)
    case Local(path: Path)
    case Server

  def pushFile(explicit: Option[Path], local: => Option[Path]): PushFile =
    explicit.map(PushFile.Given(_)).orElse(local.map(PushFile.Local(_))).getOrElse(PushFile.Server)

  /** Where the file a push would add comes from, as `calibre gaps`/`sync` report it (`original`). */
  enum GapOriginal(val label: GapOriginal.Label) derives CanEqual:
    /** `<cli.calibre.originals-dir>/<citekey>/paper.*` on this host. */
    case Local extends GapOriginal(GapOriginal.Label("local"))

    /** The original the server stores (`GET /api/sources/{ck}/original`). */
    case Stored extends GapOriginal(GapOriginal.Label("stored"))

    /** The server did not say (an older server): counted, and the push tries. */
    case Unknown extends GapOriginal(GapOriginal.Label("unknown"))

    /** No file anywhere — a metadata-only record (a citation-graph stub, a record with an abstract and no fetched
      * file). Nothing to push, so not a Calibre gap: it belongs on the acquire list.
      */
    case Missing extends GapOriginal(GapOriginal.Label("none"))

  object GapOriginal:
    type Label = Label.T
    object Label extends Newtype[String, Pure]:
      given Display[T] = Display(_.value)

  /** One source the server lists as not in Calibre, with where its original is and whether it COUNTS as a gap — the
    * `calibre gaps --quiet` reminder and `calibre sync` count only these.
    */
  final case class GapEntry(gap: CalibreGap, original: GapOriginal, counted: GapEntry.Counted)

  object GapEntry:
    enum Counted derives CanEqual:
      case Counted, Uncounted
    object Counted:
      extension (counted: Counted) def counts: Boolean = counted == Counted

  /** Pure: a gap counts when there is an original to push (on this host, or stored on the server) and the source is not
    * already canonical in Calibre (provenance `calibre`, or a Calibre id — the server leaves both out; an older or
    * differently-cased row is caught here too). A record with no original anywhere is listed, never counted.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def classify(gaps: List[CalibreGap], local: api.library.Citekey => Option[Path]): List[GapEntry] =
    gaps.map { g =>
      val original =
        if local(g.citekey).isDefined then GapOriginal.Local
        else
          g.storedOriginal match
            case Some(stored) if stored.value => GapOriginal.Stored
            case Some(_)                      => GapOriginal.Missing
            case None                         => GapOriginal.Unknown
      val inCalibre = g.calibreId.exists(_.value.trim.nonEmpty) ||
        g.provenance.exists(_.value.trim.toLowerCase(java.util.Locale.ROOT) == "calibre")
      GapEntry(
        g,
        original,
        counted = (if original != GapOriginal.Missing && !inCalibre then GapEntry.Counted.Counted
                   else GapEntry.Counted.Uncounted)
      )
    }

  /** The informational line for the records with no fetched file — never a gap. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def uncountedLine(n: Int): String =
    shown"$n ${Plural.noun(Plural.Count(n.toLong), Plural.Noun.Singular("record"))} without a fetched file — not counted; see the acquire list " +
      "(`histmersion queue acquire`); `--json` lists them."

  /** `calibre gaps` as Markdown: the counted gaps as a table, then one line for the records with no fetched file (never
    * rows of the table: they are not gaps).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdownGaps(entries: List[GapEntry]): String =
    def esc(s: String) = s.replace("|", "\\|").replace("\n", " ").trim
    val counted = entries.filter(_.counted.counts)
    val missing = entries.count(_.original == GapOriginal.Missing)
    val head =
      if counted.isEmpty then "_Every source with a fetched file is in Calibre._"
      else
        val rows = counted.map { e =>
          val g = e.gap
          s"| `${g.citekey}` | ${esc(g.title.value).take(70)} | ${g.year.fold("—")(_.toString)} " +
            shown"| ${esc(g.authors.take(2).mkString(", "))} | ${esc(g.provenance.fold("—")(_.value))} | ${e.original.label} |"
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def unlinked(
      books: List[Book],
      linked: Set[Book.Id],
      limit: Paging.Limit
  ): List[Book] =
    books.filterNot(b => linked.contains(b.id)).sortBy(b => -b.id.value).take(limit.value.max(1))

  /** The unlinked listing as Markdown: one line per entry with its file path(s), the way a person finds the books they
    * added that are not in the corpus yet.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdownUnlinked(books: List[Book], total: Paging.Page.Total): String =
    if books.isEmpty then "_Every Calibre entry is linked to a source._"
    else
      val lines = books.map { b =>
        val who = b.authors.take(3).mkString(", ") + (if b.authors.sizeIs > 3 then " et al." else "")
        val year = b.year.fold("")(y => s" ($y)")
        val files = if b.formats.isEmpty then "  - no file" else b.formats.map(f => shown"  - `$f`").mkString("\n")
        shown"- **${b.id}** · ${b.title}$year — ${if who.isEmpty then "no author" else who}\n$files"
      }
      val shown = if total.value > books.size then s"${books.size} of $total" else books.size.toString
      (List(
        shown"### $shown Calibre ${Plural.noun(Plural.Count(total.value.toLong), Plural.Noun.Singular("entry"), Plural.Noun.Plural("entries"))} not linked to any source (newest first)",
        ""
      )
        ++ lines ++ List(
          "",
          "_Import one with `histmersion calibre import <id>`, or link it to an existing record with " +
            "`histmersion calibre link <citekey> <id>`._"
        )).mkString("\n")

  /** Render a Calibre search result as Markdown (the CLI's own output — the server never sees these). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def markdownSearch(books: List[Book]): String =
    if books.isEmpty then "_No matching books in the Calibre library._"
    else
      val rows = books.map { b =>
        val fmts = b.formats.flatMap(_.value.split("\\.").lastOption).map(_.toUpperCase).distinct.mkString(", ")
        // a multi-author paper in Calibre can carry 200 names; a table cell shows the first few
        val who = b.authors.take(3).mkString(", ") + (if b.authors.sizeIs > 3 then " et al." else "")
        shown"| ${b.id} | ${b.title.value.replace("|", "\\|")} | ${who.replace("|", "\\|")} " +
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
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
    def titleMatches(a: api.library.Title, b: api.library.Title): Boolean =
      val (x, y) = (norm(a.value), norm(b.value))
      x.nonEmpty && y.nonEmpty && (x.startsWith(y) || y.startsWith(x))

    /** "Güterbock, Hans G." and "Hans G. Güterbock" are the same surname; a bracketed role or life span ("Ken Dowden
      * (ed.)", "Gimbutienė, Marija (1921-1994)") is not part of the name.
      */
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def surname(author: AuthorName): Source.AuthorSurname =
      val name = author.value.replaceAll("\\([^)]*\\)", " ").trim
      Source.AuthorSurname(
        norm(if name.contains(",") then name.takeWhile(_ != ',') else name.split("\\s+").last)
      )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def authorsMatch(a: List[AuthorName], b: List[AuthorName]): Boolean =
      (a.map(surname).toSet & b.map(surname).toSet).exists(_.value.nonEmpty)

    /** Calibre's placeholder year for "unknown" is 101; anything before 1000 is not a real pubdate. */
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def year(b: Book): Option[Book.Year] =
      b.pubdate.flatMap(_.value.take(4).toIntOption).filter(_ >= 1000).map(n => Book.Year(n.toLong))

    /** The metadata a scrape or a bad import leaves: blank/Unknown, or a title that is a web page. */
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
    def junk(b: Book): Boolean =
      isBlank(b.title.value) || b.title.value.length > 300 || b.authors.isEmpty ||
        b.authors.exists(a => isBlank(a.value) || a.value.toLowerCase(java.util.Locale.ROOT).contains("unknown"))

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def issues(r: AuditRecord, b: Book): List[AuditIssue] =
      List(
        Option.when(junk(b))(AuditIssue.Junk),
        Option
          .when(!junk(b) && !titleMatches(api.library.Title(r.title.value), b.title))(AuditIssue.Title),
        Option.when(!junk(b) && !authorsMatch(r.authors.map(a => AuthorName(a.value)), b.authors))(AuditIssue.Authors),
        Option.when(r.year.isDefined && year(b).isDefined && r.year.map(y => Book.Year(y.value.toLong)) != year(b))(
          AuditIssue.Year
        )
      ).flatten

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def audit(records: List[AuditRecord], books: Map[Book.Id, Book]): List[AuditFinding] =
      records.groupBy(_.calibreId.value).toList.sortBy(kv => kv._1: Int).flatMap { (idValue, rs) =>
        val id = AuditFinding.CalibreId(idValue)
        val keys = rs.map(r => AuditFinding.Citekeys(r.citekey.value)).sorted
        books.get(Book.Id(idValue)) match
          case None    => List(AuditFinding(id, keys, List(AuditIssue.Missing), None, rs.minBy(_.citekey.value)))
          case Some(b) =>
            val (best, found) = rs.map(r => r -> issues(r, b)).minBy((r, is) => (is.size, r.citekey.value))
            if found.isEmpty then Nil else List(AuditFinding(id, keys, found, Some(b), best))
      }

    private def cell(s: String): String = s.replace("|", "\\|").replace("\n", " ").take(90)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def markdown(findings: List[AuditFinding], checked: CollectionAudit.Checked): String =
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
                  if f.fixable then shown"`histmersion calibre update ${f.record.citekey}`"
                  else "re-push, or clear the source's calibre id"
                shown"| ${f.calibreId} | ${f.citekeys.mkString(", ")} | ${f.issues.map(_.label).mkString("; ")} | ${cell(corpus)} | ${cell(cal)} | $todo |"
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
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def year: Option[Book.Year] = pubdate
      .map(_.value.take(4))
      .filter(_.forall(_.isDigit))
      .flatMap(_.toIntOption)
      .filter(_ >= Acquire.MinCitekeyYear)
      .map(n => Book.Year(n.toLong))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def doi: Option[api.library.Doi] =
      identifiers.get(Book.IdentifiersKey("doi")).map(v => api.library.Doi(v.value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def isbn: Option[CalibreGap.Isbn] = identifiers.get(Book.IdentifiersKey("isbn")).map(v => CalibreGap.Isbn(v.value))

    /** The file to import: the requested format, else PDF, else EPUB (both carry a text layer). */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def pick(format: Option[Book.Format]): Option[Book.Formats] =
      val wanted = format.map(_.value.toLowerCase).toList ++ List("pdf", "epub")
      wanted.iterator.map(ext => formats.find(_.value.toLowerCase.endsWith("." + ext))).collectFirst { case Some(f) =>
        f
      }

  object Book:
    type Format = Format.T
    object Format extends Newtype[String, Pure]
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Year = api.catalogue.Year
    val Year: api.catalogue.Year.type = api.catalogue.Year

    type Id = Id.T
    object Id extends Newtype[Int, Pure]:
      given Display[T] = Display(id => s"${id.value}")
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Title = api.library.Title
    val Title: api.library.Title.type = api.library.Title
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Authors = AuthorName
    val Authors: AuthorName.type = AuthorName
    type Pubdate = Pubdate.T
    object Pubdate extends Newtype[String, Pure]:
      given Display[T] = Display(_.value)
    export CollectionEntry.{Publisher, Tags}
    type Comments = Comments.T
    object Comments extends Newtype[String, Pure]:
      given Display[T] = Display(_.value)
      extension (comments: Comments) def nonEmpty: Boolean = comments.value.nonEmpty
    export CollectionEntry.IdentifiersKey
    type IdentifiersValue = IdentifiersValue.T
    object IdentifiersValue extends Newtype[String, Pure]:
      given Display[T] = Display(_.value)
      extension (value: IdentifiersValue)
        def trim: IdentifiersValue = IdentifiersValue(value.value.trim)
        def nonEmpty: Boolean = value.value.nonEmpty
        def sameIgnoringCase(other: IdentifiersValue): Boolean = value.value.equalsIgnoreCase(other.value)
    export CollectionEntry.Formats
    type Cover = Cover.T
    object Cover extends Newtype[String, Pure]:
      given Display[T] = Display(_.value)
      extension (cover: Cover)
        def trim: Cover = Cover(cover.value.trim)
        def nonEmpty: Boolean = cover.value.nonEmpty

  /** One field `calibre update` changes: what Calibre has now and what it writes. */
  final case class Change(field: Field, from: Change.From, to: Change.To)

  object Change:
    export CollectionFieldChange.{From, To}

  /** A field `calibre update` writes, by its `calibredb set_metadata --field` name. */
  enum Field(val name: Field.Name):
    case Title extends Field(Field.Name("title"))
    case Authors extends Field(Field.Name("authors"))
    case Pubdate extends Field(Field.Name("pubdate"))
    case Identifiers extends Field(Field.Name("identifiers"))
    case Tags extends Field(Field.Name("tags"))
    case Publisher extends Field(Field.Name("publisher"))
    case Comments extends Field(Field.Name("comments"))

  object Field:
    export CollectionFieldValue.Name

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
    ) derives JsonCodec
    object Book:
      export Calibre.Book.Id
      @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
      type Title = api.library.Title
      val Title: api.library.Title.type = api.library.Title
      @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
      type Authors = AuthorName
      val Authors: AuthorName.type = AuthorName
      @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
      @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
      type Year = Calibre.Book.Year
      @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
      val Year: Calibre.Book.Year.type = Calibre.Book.Year
      export CollectionEntry.{IdentifiersKey, Publisher, Tags}
      export Calibre.Book.IdentifiersValue
      export CollectionEntry.Formats

      @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
      def of(b: Calibre.Book): Book =
        Book(b.id, b.title, b.authors, b.year, b.publisher, b.tags, b.identifiers, b.formats)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def of(e: CollectionEntry): Book =
        Book(
          Id(e.id.value.toIntOption.getOrElse(0)),
          e.title,
          e.authors,
          e.year,
          e.publisher,
          e.tags,
          e.identifiers.map((k, v) => k -> IdentifiersValue(v.value)),
          e.formats
        )

    /** `calibre link --json`, `calibre push --json`. */
    final case class Linked(citekey: api.library.Citekey, calibreId: Linked.CalibreId) derives JsonCodec

    object Linked:
      export Calibre.Book.Id as CalibreId

    /** `calibre push --replace --json`: `replaced` is the format swapped in. */
    final case class Replaced(
        citekey: api.library.Citekey,
        calibreId: Replaced.CalibreId,
        replaced: Replaced.Value
    ) derives JsonCodec

    object Replaced:
      export Calibre.Book.Id as CalibreId
      type Value = Value.T
      object Value extends Newtype[String, Pure]

    /** `calibre update <citekey> --json`: the fields written. */
    final case class Updated(
        citekey: api.library.Citekey,
        calibreId: Updated.CalibreId,
        changed: List[Updated.Changed]
    ) derives JsonCodec

    object Updated:
      export Calibre.Book.Id as CalibreId
      export CollectionUpdatedOne.Changed

    final case class Change(field: Change.Field, from: Change.From, to: Change.To) derives JsonCodec

    object Change:
      export CollectionFieldChange.{Field, From, To}

    /** `calibre update --all --json`: one entry's outcome. */
    final case class UpdateRow(
        calibreId: UpdateRow.CalibreId,
        citekeys: List[UpdateRow.Citekeys],
        state: UpdateRow.State,
        changes: List[Change],
        reasons: List[Problem]
    ) derives JsonCodec
    object UpdateRow:
      export Calibre.Book.Id as CalibreId
      export CollectionUpdateRow.{Citekeys, State}

      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
          case UpdateOutcome.Failed(_, r) => List(r)
          case UpdateOutcome.Refused(r)   => List(r)
          case _                          => Nil
        UpdateRow(
          e.calibreId,
          e.citekeys.map(k => Citekeys(k.value)),
          State(state),
          changes.map(ch => Change(Change.Field(ch.field.name.value), ch.from, ch.to)),
          reason
        )
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def of(r: CollectionUpdateRow): UpdateRow =
        UpdateRow(
          CalibreId(r.id.value.toIntOption.getOrElse(0)),
          r.citekeys,
          r.state,
          r.changes.map(ch => Change(ch.field, ch.from, ch.to)),
          r.reasons
        )

    /** `calibre audit --json`: one entry that disagrees with its record; `fix` = the citekey whose record
      * `calibre update` would write over it, absent when the entry is missing.
      */
    final case class AuditRow(
        calibreId: AuditRow.CalibreId,
        citekeys: List[AuditRow.Citekeys],
        issues: List[AuditRow.Issues],
        fix: Option[AuditRow.Fix]
    ) derives JsonCodec
    object AuditRow:
      export Calibre.Book.Id as CalibreId
      export CollectionAudit.Row.{Citekeys, Codes as Issues, Fix}

      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def of(f: AuditFinding): AuditRow =
        AuditRow(
          CalibreId(f.calibreId.value),
          f.citekeys.map(k => Citekeys(k.value)),
          f.issues.map(i => Issues(i.toString)),
          Option.when(f.fixable)(Fix(f.record.citekey.value))
        )
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def of(r: CollectionAudit.Row): AuditRow =
        AuditRow(CalibreId(r.id.value.toIntOption.getOrElse(0)), r.citekeys, r.codes, r.fix)

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
    ) derives JsonCodec
    object GapRow:
      @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
      type Citekey = api.library.Citekey
      val Citekey: api.library.Citekey.type = api.library.Citekey
      @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
      type Title = api.library.Title
      val Title: api.library.Title.type = api.library.Title
      @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
      type Authors = AuthorName
      val Authors: AuthorName.type = AuthorName
      @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
      type Doi = api.library.Doi
      val Doi: api.library.Doi.type = api.library.Doi
      @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
      @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
      type Year = Calibre.Book.Year
      @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
      val Year: Calibre.Book.Year.type = Calibre.Book.Year
      export Source.Venue
      export CollectionGapRow.{Provenance, Summary, Topics}
      export Source.CalibreId
      export CollectionGapRow.{Counted, Isbn, Original, StoredOriginal}

      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def of(e: GapEntry): GapRow =
        val g = e.gap
        GapRow(
          g.citekey,
          g.title,
          g.authors.map(a => Authors(a.value)),
          g.doi,
          g.year,
          g.venue,
          g.summary.map(x => Summary(x.value)),
          g.topics.map(x => Topics(x.value)),
          g.provenance.map(x => Provenance(x.value)),
          g.calibreId,
          g.isbn.map(x => Isbn(x.value)),
          g.storedOriginal.map(x => StoredOriginal(x.value)),
          Original(e.original.label.value),
          Counted(e.counted.counts)
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

    final case class Gaps(count: Gaps.Count, uncounted: Gaps.Uncounted, total: Gaps.Total, gaps: List[GapRow])
        derives JsonCodec
    object Gaps:
      export CollectionGaps.{Count, Total, Uncounted}

      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def of(entries: List[GapEntry]): Gaps =
        val n = entries.count(_.counted.counts)
        Gaps(Count(n), Uncounted(entries.size - n), Total(entries.size), entries.map(GapRow.of))
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
    enum Replace derives CanEqual:
      case Replace, Keep
    object Replace:
      extension (replace: Replace) def replaces: Boolean = replace == Replace
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Title = api.library.Title
    val Title: api.library.Title.type = api.library.Title
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Authors = AuthorName
    val Authors: AuthorName.type = AuthorName
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Doi = api.library.Doi
    val Doi: api.library.Doi.type = api.library.Doi
    export CalibreGap.Isbn
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Year = Book.Year
    val Year: Book.Year.type = Book.Year
    export CollectionEntry.Publisher
    export Source.Summary
    export CollectionEntry.Tags
