package com.histmersion
package plugins.calibre
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.problem.{External, Problem, Problems}

import com.histmersion.plugin.EnvVar

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import java.text.Normalizer
import com.histmersion.plugin.LocalFiles
import com.histmersion.plugin.CollectionQuery
import com.histmersion.plugin.CollectionFile
import com.histmersion.plugin.Processes
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.plugin.Plugin
import com.histmersion.lint.{tolerated, LintRule}

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
final class Calibre(cfg: Calibre.Library, shell: Shell)(using files: LocalFiles):
  import Calibre.{said, Book}

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def libArgs: List[Calibre.Argument] = Calibre.argv("--with-library", cfg.library.value)

  /** The Calibre GUI holds an exclusive lock on the library for writes. */
  def guiRunning: Boolean = shell.isRunning(cfg.guiProcess)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def libraryExists: Boolean = files.isFile(Path.of(cfg.library.value, "metadata.db"))

  // ── reading ─────────────────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def search(
      query: CollectionQuery.Text,
      limit: Calibre.SearchLimit = Calibre.SearchLimit(20)
  ): Either[Problems, List[Book]] =
    viaCalibredb(Some(query), limit.value).orElse(viaSqlite(Some(query), None, limit.value))

  /** The whole library (read-only), for the audit: one listing instead of a call per entry. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def all(limit: Calibre.AllLimit = Calibre.AllLimit(1000000)): Either[Problems, List[Book]] =
    viaCalibredb(None, limit.value).orElse(viaSqlite(None, None, limit.value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def byId(id: Book.Id): Either[Problems, Option[Book]] =
    viaCalibredb(Some(CollectionQuery.Text(s"id:$id")), 1)
      .map(_.find(_.id == id))
      .orElse(viaSqlite(None, Some(id), 1).map(_.headOption))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def viaCalibredb(
      query: Option[CollectionQuery.Text],
      limit: Int
  ): Either[Problems, List[Book]] =
    val cmd = Calibre.argv(cfg.calibredb.value, "list") ++ libArgs ++
      Calibre.argv(
        "--for-machine",
        "--fields",
        "id,title,authors,pubdate,publisher,tags,comments,identifiers,formats,cover",
        "--limit",
        limit.toString
      ) ++ query.toList.flatMap(q => Calibre.argv("--search", q.value))
    shell.run(cmd).flatMap(Calibre.parseMachineJson)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def viaSqlite(
      query: Option[CollectionQuery.Text],
      id: Option[Book.Id],
      limit: Int
  ): Either[Problems, List[Book]] =
    val where = id match
      case Some(n) => s"b.id = ${n.value}"
      case None    =>
        val like = Calibre.sqlLiteral(CollectionQuery.Text("%" + query.fold("")(_.value) + "%"))
        s"(b.title LIKE $like OR b.author_sort LIKE $like)"
    val sql = Calibre.sqliteQuery(Calibre.SqlWhere(where), Calibre.SqliteQueryLimit(limit))
    shell
      .run(
        Calibre
          .argv(cfg.sqlite.value, "-readonly", "-json", Path.of(cfg.library.value, "metadata.db").toString, sql.value)
      )
      .flatMap(Calibre.parseSqliteJson(_, cfg.library))

  // ── writing (push) ──────────────────────────────────────────────────────────────────────────

  /** `calibredb add` + `set_metadata` — the port of `to_calibre.py`. Returns the NEW Calibre id.
    *
    * The metadata guard is the point: Calibre entries with an empty or "Unknown" title/author are unfindable junk, and
    * the whole reason this pipeline pushes metadata at all is that Calibre's own metadata sources key on ISBN and
    * cannot identify a journal article.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def add(file: Path, meta: Calibre.Push): Either[Problems, Book.Id] =
    for
      _ <- Calibre.guard(meta.title, meta.authors)
      _ <- Either.cond(!guiRunning, (), GuiRunning)
      _ <- Either.cond(files.isFile(file), (), Problem.Io.Wrong(Problem.Io.Path(s"$file"), Problem.Io.Kind.File))
      out <- shell.run(addCmd(file, meta))
      id <- Calibre
        .parseAddedId(out)
        .toRight(
          Problem.Io.Corrupt(
            Problem.Io.Format.Text,
            Problem.Where("calibredb add's answer"),
            Problem.Io.Damage.Unreadable(External.Reported.of(out.value.trim))
          )
        )
      _ <- setMetadata(id, meta)
    yield id

  /** Bring an EXISTING entry in line with the corpus record — for when a record's title or authors change after the
    * push (a chapter split out of a volume left the volume's entry titled as the chapter), and to leave the entry
    * complete: identifiers merged, tags = the record's topics, the record's year. What is written is
    * [[Calibre.updatePlan]]; nothing is run when the entry already agrees. Returns what changed.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def update(
      id: Book.Id,
      meta: Calibre.Push
  ): Either[Problems, List[Calibre.Change]] =
    for
      _ <- Calibre.guard(meta.title, Calibre.normaliseAuthors(meta.authors))
      _ <- Either.cond(!guiRunning, (), GuiRunning)
      cur <- byId(id).flatMap(_.toRight(Problem.NotFound.Entity(Problem.TypeName.of(Book.Id), Problem.Raw(s"$id"))))
      changes <- Calibre.updatePlan(meta, cur)
      _ <- write(id, changes)
    yield changes

  /** `calibre update --all`: plan the update of every linked entry against ONE listing of the library; with `apply`,
    * write the entries that would change. An entry linked from more than one record (a chapter and its volume) is left
    * alone — which record it should show is a decision for a person, `calibre update <citekey>`.
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def updateAll(linked: List[LinkedRecord], apply: Calibre.UpdateAllApply): Either[Problems, List[UpdateEntry]] =
    for
      _ <- Either.cond(!apply.value || !guiRunning, (), GuiRunning)
      books <- all()
    yield Calibre.planAll(linked, books.map(b => b.id -> b).toMap).map { e =>
      e.outcome match
        case UpdateOutcome.Change(cs) if apply.value =>
          e.copy(outcome =
            write(Book.Id(e.calibreId.value), cs)
              .fold(
                why => UpdateOutcome.Failed(cs, why),
                _ => UpdateOutcome.Applied(cs)
              )
          )
        case _ => e
    }

  /** One `set_metadata` with the planned fields; nothing at all when there are none. */
  private def write(id: Book.Id, changes: List[Calibre.Change]): Either[Problems, Unit] =
    if changes.isEmpty then Right(())
    else shell.run(Calibre.setMetadataCmd(cfg.calibredb, libArgs, id, changes)).map(_ => ())

  private val GuiRunning: Problem = Problem.Conflict.Busy(Problem.Conflict.Activity.CalibreGui)

  /** Set named fields on an existing entry — the explicit, reviewed counterpart of [[update]] (`enrich-calibre` applies
    * approved fixes with it). Same guards as every other write.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def set(id: Book.Id, fields: List[(Calibre.Field.Name, Calibre.Field.Value)]): Either[Problems, Unit] =
    for
      cmd <- Calibre.setCmd(cfg.calibredb, libArgs, id, fields)
      _ <- Either.cond(!guiRunning, (), GuiRunning)
      _ <- byId(id).flatMap(_.toRight(Problem.NotFound.Entity(Problem.TypeName.of(Book.Id), Problem.Raw(s"$id"))))
      _ <- shell.run(cmd)
    yield ()

  /** Replace the file of an EXISTING entry: `calibredb add_format`, which replaces the entry's format of the same type.
    * Unlike [[add]] it never creates an entry, so the link stays valid.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def replaceFormat(id: Book.Id, file: Path): Either[Problems, CollectionFile.Format] =
    for
      fmt <- Calibre.formatOf(file)
      _ <- Either.cond(!guiRunning, (), GuiRunning)
      _ <- Either.cond(files.isFile(file), (), Problem.Io.Wrong(Problem.Io.Path(s"$file"), Problem.Io.Kind.File))
      _ <- byId(id).flatMap(_.toRight(Problem.NotFound.Entity(Problem.TypeName.of(Book.Id), Problem.Raw(s"$id"))))
      _ <- shell.run(Calibre.replaceFormatCmd(cfg.calibredb, libArgs, id, file))
    yield fmt

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def addCmd(file: Path, meta: Calibre.Push): List[Calibre.Argument] =
    Calibre.argv(cfg.calibredb.value, "add", file.toString) ++ libArgs ++
      Calibre.argv("--title", meta.title.value.trim, "--authors", meta.authors.mkString(" & "), "--duplicates") ++
      meta.doi.toList.flatMap(d => Calibre.argv("--identifier", s"doi:$d")) ++
      meta.isbn.toList.flatMap(i => Calibre.argv("--isbn", i.value)) ++
      (if meta.tags.isEmpty then Nil else Calibre.argv("--tags", meta.tags.mkString(",")))

  /** The fields `calibredb add` cannot set. Best-effort: a failure here leaves a usable entry. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def setMetadata(id: Book.Id, meta: Calibre.Push): Either[Problems, Unit] =
    val fields = List(
      meta.publisher.map("publisher:" + _),
      meta.year.map(y => s"pubdate:$y-01-01"),
      meta.summary.map("comments:" + _)
    ).flatten
    if fields.isEmpty then Right(())
    else
      val cmd = Calibre.argv(cfg.calibredb.value, "set_metadata") ++ libArgs ++ Calibre.argv(id.toString) ++
        fields.flatMap(f => Calibre.argv("--field", f))
      shell
        .run(cmd)
        .map(_ => ())
        .left
        .map(e => Problems.of(said(s"added as $id, but its metadata was not set"), e))

/** The PURE half: parsing what the two readers print, the metadata guard, and SQL assembly. */

/** The pure half: parsing what the two readers print, the metadata guard, the update plan and the commands. */
object Calibre:
  export Processes.RunArgv as Argument
  type Author = Author.T
  object Author extends Newtype[String, Pure]
  type SqlLiteral = SqlLiteral.T
  object SqlLiteral extends Newtype[String, Pure]
  type SqlWhere = SqlWhere.T
  object SqlWhere extends Newtype[String, Pure]
  type SqlQuery = SqlQuery.T
  object SqlQuery extends Newtype[String, Pure]

  type SearchLimit = SearchLimit.T
  object SearchLimit extends Newtype[Int, Pure]
  type AllLimit = AllLimit.T
  object AllLimit extends Newtype[Int, Pure]
  type UpdateAllApply = UpdateAllApply.T
  object UpdateAllApply extends Newtype[Boolean, Pure]
  type SqliteQueryLimit = SqliteQueryLimit.T
  object SqliteQueryLimit extends Newtype[Int, Pure]

  private def argv(parts: String*): List[Argument] = parts.toList.map(part => Argument(part))

  /** Values that mean "nobody filled this in". Pushing one of these into Calibre creates an entry nobody will ever find
    * again, so both `import` and `push` refuse.
    */
  private val Blank = Set("", "unknown", "unknown author", "untitled", "n/a", "?", "none")

  /** `tool`'s answer is not JSON: what the reader said. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def unreadable(tool: String, e: Any): Problem =
    Problem.Io.Corrupt(
      Problem.Io.Format.Json,
      Problem.Where(s"$tool's answer"),
      Problem.Io.Damage.Unreadable(External.Reported.of(s"$e"))
    )

  private[calibre] def isBlank(s: String): Boolean = Blank.contains(Option(s).getOrElse("").trim.toLowerCase)

  /** The metadata guard, shared by `import` (Calibre → corpus) and `push` (corpus → Calibre). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def guard(title: Push.Title, authors: List[Author]): Either[Problems, Unit] =
    val missing = List(
      Option.when(isBlank(title.value))("title"),
      Option.when(authors.forall(a => isBlank(a.value)))("author")
    ).flatten.map(f => Problem.Invalid.Missing.here(Problem.Field(f)))
    Problems.from(missing.toVector).toLeft(())

  /** What the plugin says in its own words: what a failure left behind, how to fix the library's settings. */
  def said(text: String): Problem =
    Problem.Plugin.Said(Problem.Plugin.Id("calibre"), Problem.Plugin.Claim.Failed, External.PluginSaid.of(text))

  // ── import: citekeys and where they land ─────────────────────────────────────────────────────

  /** The formats `calibre push --replace` puts into an entry: the two the corpus imports. */
  val ReplaceableFormats: List[CollectionFile.Format] =
    List(CollectionFile.Format("pdf"), CollectionFile.Format("epub"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def formatOf(file: Path): Either[Problems, CollectionFile.Format] =
    val ext = CollectionFile.Format(file.getFileName.toString.reverse.takeWhile(_ != '.').reverse.toLowerCase)
    Either.cond(
      ReplaceableFormats.contains(ext),
      ext,
      Problem.Invalid.Unknown(
        Problem.TypeName.named[CollectionFile],
        Problem.Raw(s"$ext"),
        ReplaceableFormats.toVector.map(f => Problem.Raw(s"$f")),
        Vector(Problem.Field(s"${file.getFileName}"))
      )
    )

  /** `calibredb add_format` replaces the entry's existing format of the same type (that is its default;
    * `--dont-replace` would be the opposite).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def replaceFormatCmd(
      calibredb: Settings.ExecutablePath,
      libArgs: List[Argument],
      id: Book.Id,
      file: Path
  ): List[Argument] =
    argv(calibredb.value, "add_format") ++ libArgs ++ argv(id.toString, file.toString)

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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def updatePlan(
      meta: Push,
      current: Book
  ): Either[Problems, List[Change]] =
    val authors = normaliseAuthors(meta.authors)
    guard(meta.title, authors).map { _ =>
      def empty(v: Option[String]) = v.forall(s => isBlank(s) || s.trim == "-")
      def change(f: Field, from: String, to: String) =
        Option.when(from != to)(Change(f, Change.From(from), Change.To(to)))
      val ids = mergeIdentifiers(current.identifiers, meta.doi, meta.isbn)
      List(
        // compared and written trimmed: a stray space is not a different title
        Option.when(current.title.value.trim != meta.title.value.trim)(
          Change(Field.Title, Change.From(current.title.value), Change.To(meta.title.value.trim))
        ),
        change(
          Field.Authors,
          current.authors.mkString(" & "),
          authors.mkString(" & ")
        ),
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
          meta.tags.nonEmpty && meta.tags.map(_.value).distinct.sorted != current.tags.map(_.value).distinct.sorted
        )(
          Change(
            Field.Tags,
            Change.From(current.tags.mkString(",")),
            Change.To(meta.tags.distinct.mkString(","))
          )
        ),
        meta.publisher
          .filter(p => empty(current.publisher.map(_.value)) && !isBlank(p.value))
          .map(p => Change(Field.Publisher, Change.From(current.publisher.fold("")(_.value)), Change.To(p.value))),
        meta.summary
          .filter(d => empty(current.comments.map(_.value)) && !isBlank(d.value))
          .map(d => Change(Field.Comments, Change.From(current.comments.fold("")(_.value)), Change.To(d.value)))
      ).flatten
    }

  /** `calibredb set_metadata` writing `changes` into entry `id`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def setMetadataCmd(
      calibredb: Settings.ExecutablePath,
      libArgs: List[Argument],
      id: Book.Id,
      changes: List[Change]
  ): List[Argument] =
    argv(calibredb.value, "set_metadata") ++ libArgs ++ argv(id.toString) ++
      changes.flatMap(c => argv("--field", s"${c.field.name}:${c.to}"))

  /** Calibre's identifiers with the record's merged in: the DOI is the record's (it names the work the corpus cites),
    * an ISBN only fills a gap, and nothing Calibre has is ever dropped. A DOI is case-insensitive, so one differing
    * only in case is left as Calibre has it. A value with a comma cannot cross `--field identifiers:` (the comma
    * separates the pairs), so it is left out.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def mergeIdentifiers(
      current: Map[Book.IdentifiersKey, Book.IdentifiersValue],
      doi: Option[Push.Doi],
      isbn: Option[Push.Isbn]
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
        .filterNot(_ => current.get(Book.IdentifiersKey("isbn")).exists(_.value.trim.nonEmpty))
        .map(i => Book.IdentifiersKey("isbn") -> Book.IdentifiersValue(i))

  /** `type:value,type:value`, as `--field identifiers:` takes it, in a stable order. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def identifiersValue(ids: Map[Book.IdentifiersKey, Book.IdentifiersValue]): String =
    ids.toList.sortBy(_._1.value).map((k, v) => s"$k:$v").mkString(",")

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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def normaliseAuthor(raw: Author): Author =
    val unbracketed = (raw).value.replaceAll("\\([^)]*\\)|\\[[^\\]]*\\]", " ")
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
    Author(ordered.replaceAll("\\s+", " ").trim)

  /** [[normaliseAuthor]] over a list, dropping names that end up empty and repeats. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def normaliseAuthors(authors: List[Author]): List[Author] =
    authors.map(normaliseAuthor).filter(_.value.nonEmpty).distinct

  /** Plan `update --all`: one [[UpdateEntry]] per linked Calibre id, in id order. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def planAll(
      linked: List[LinkedRecord],
      books: Map[Book.Id, Book]
  ): List[UpdateEntry] =
    linked.groupBy(_.calibreId).toList.sortBy(_._1).map { (id, rs) =>
      val keys = rs.map(_.citekey).distinct.sorted
      val outcome =
        if keys.sizeIs > 1 then UpdateOutcome.Shared
        else
          books.get(Book.Id(id.value)) match
            case None    => UpdateOutcome.Missing
            case Some(b) =>
              updatePlan(rs.head.meta, b).fold(
                why => UpdateOutcome.Refused(why),
                cs => if cs.isEmpty then UpdateOutcome.UpToDate else UpdateOutcome.Change(cs)
              )
      UpdateEntry(UpdateEntry.CalibreId(id.value), keys.map(k => UpdateEntry.Citekeys(k.value)), outcome)
    }

  /** The fields `calibre set` may write. Formats, covers and the file itself are Calibre's business. */
  val SettableFields: List[Field.Name] =
    List(
      Field.Name("title"),
      Field.Name("authors"),
      Field.Name("publisher"),
      Field.Name("pubdate"),
      Field.Name("languages"),
      Field.Name("identifiers"),
      Field.Name("tags"),
      Field.Name("comments"),
      Field.Name("series")
    )

  /** `calibredb set_metadata` for [[Calibre.set]], or why it is refused: an unknown field, nothing to set, or a
    * blank/"Unknown" title or author (the metadata guard).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def setCmd(
      calibredb: Settings.ExecutablePath,
      libArgs: List[Argument],
      id: Book.Id,
      fields: List[(Field.Name, Field.Value)]
  ): Either[Problems, List[Argument]] =
    val unknown = fields.map(_._1).filterNot(SettableFields.contains)
    def value(k: Field.Name) = fields.collectFirst { case (`k`, v) => v }
    for
      _ <- Either.cond(
        fields.nonEmpty,
        (),
        Problem.Invalid.Missing.here(Problem.Field("--field"))
      )
      _ <- Either.cond(
        unknown.isEmpty,
        (),
        Problem.Invalid.Unknown(
          Problem.TypeName.of(Field.Name),
          Problem.Raw(unknown.mkString(", ")),
          SettableFields.toVector.map(f => Problem.Raw(s"$f")),
          Vector(Problem.Field("--field"))
        )
      )
      _ <- value(Field.Name("title")).fold[Either[Problems, Unit]](Right(()))(t =>
        Either.cond(!isBlank(t.value), (), Problem.Invalid.Missing.here(Problem.Field("title")))
      )
      _ <- value(Field.Name("authors")).fold[Either[Problems, Unit]](Right(()))(a =>
        Either.cond(
          a.value.split("&").map(_.trim).forall(x => !isBlank(x)),
          (),
          Problem.Invalid.Missing.here(Problem.Field("authors"))
        )
      )
    yield argv(calibredb.value, "set_metadata") ++ libArgs ++ argv(id.toString) ++
      fields.flatMap((k, v) => argv("--field", s"$k:$v"))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseAddedId(out: Processes.Result.Out): Option[Book.Id] =
    AddedIds
      .findFirstMatchIn(out.value)
      .map(_.group(1))
      .flatMap(_.split(",").headOption)
      .flatMap(_.trim.toIntOption)
      .map(n => Book.Id(n))

  /** `calibredb list --for-machine`: authors as `A & B`, tags/formats as arrays, identifiers as an object, formats as
    * ABSOLUTE paths.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseMachineJson(out: Processes.Result.Out): Either[Problems, List[Book]] =
    if out.value.trim.isEmpty then Right(Nil) // no match: nothing is printed at all
    else
      RawJson
        .parse(out.value)
        .left
        .map(e => unreadable("calibredb", e))
        .map(_.asArray.toList.flatten.flatMap { j =>
          val c = j.cursor
          c.downField("id").as[Int].toOption.map { id =>
            Book(
              id = Book.Id(id),
              title = Book.Title(c.downField("title").as[String].getOrElse("")),
              authors = splitAuthors(c.downField("authors").as[String].getOrElse("")),
              pubdate =
                (c.downField("pubdate").as[String].toOption).map(refactorValue14 => Book.Pubdate(refactorValue14)),
              publisher = (c
                .downField("publisher")
                .as[String]
                .toOption
                .filter(_.nonEmpty))
                .map(refactorValue15 => Book.Publisher(refactorValue15)),
              tags = (c
                .downField("tags")
                .as[List[String]]
                .getOrElse(Nil))
                .map(refactorValue16 => Book.Tags(refactorValue16)),
              comments = (c
                .downField("comments")
                .as[String]
                .toOption
                .filter(_.nonEmpty))
                .map(refactorValue17 => Book.Comments(refactorValue17)),
              identifiers = (c
                .downField("identifiers")
                .as[Map[String, String]]
                .getOrElse(Map.empty))
                .iterator
                .map { case (refactorKey, refactorItem) =>
                  (Book.IdentifiersKey(refactorKey), Book.IdentifiersValue(refactorItem))
                }
                .toMap,
              formats = (c
                .downField("formats")
                .as[List[String]]
                .getOrElse(Nil))
                .map(refactorValue18 => Book.Formats(refactorValue18)),
              cover = (c
                .downField("cover")
                .as[String]
                .toOption
                .map(_.trim)
                .filter(_.nonEmpty))
                .map(refactorValue19 => Book.Cover(refactorValue19))
            )
          }
        })

  /** `sqlite3 -json`: every column is a scalar, the multi-valued ones joined by [[sqliteQuery]], and `formats` relative
    * to the library root.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseSqliteJson(
      out: Processes.Result.Out,
      libraryDir: Library.Value
  ): Either[Problems, List[Book]] =
    // `sqlite3 -json` prints NOTHING for an empty result, not `[]` — that was "exhausted input"
    if out.value.trim.isEmpty then Right(Nil)
    else
      RawJson
        .parse(out.value)
        .left
        .map(e => unreadable("sqlite3", e))
        .map(_.asArray.toList.flatten.flatMap { j =>
          val c = j.cursor
          def s(f: String) = c.downField(f).as[String].toOption.map(_.trim).filter(_.nonEmpty)
          c.downField("id").as[Int].toOption.map { id =>
            Book(
              id = Book.Id(id),
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
                .map(f => Book.Formats(Path.of(libraryDir.value, f).toString)),
              cover = s("cover").map(f => Book.Cover(Path.of(libraryDir.value, f).toString))
            )
          }
        })

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def splitAuthors(s: String): List[Author] =
    s.split(" & ").map(_.trim).filter(_.nonEmpty).toList.map(name => Author(name))

  /** Single-quote a SQL literal for the `sqlite3` command line. The only user value that reaches SQL is a search term,
    * and it goes through here.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sqlLiteral(s: CollectionQuery.Text): SqlLiteral = SqlLiteral("'" + s.value.replace("'", "''") + "'")

  /** The read-only projection that mirrors `calibredb --for-machine` (Calibre's schema: `books` plus link tables for
    * authors/tags/publishers/identifiers, and `data` for the files).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def sqliteQuery(where: SqlWhere, limit: SqliteQueryLimit): SqlQuery =
    SqlQuery(
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
    )

  /** The PURE half of `histmersion calibre audit`: compare every linked entry with its record. */
  object Audit:

    /** Lower-case ASCII words: accents, punctuation and spacing are not disagreements. */

    private[calibre] def norm(s: String): String =
      Normalizer
        .normalize(s, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(java.util.Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim

    /** Equal after [[norm]], or one extends the other: Calibre often keeps or drops a subtitle. */
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def titleMatches(a: AuditRecord.Title, b: Book.Title): Boolean =
      val (x, y) = (norm(a.value), norm(b.value))
      x.nonEmpty && y.nonEmpty && (x.startsWith(y) || y.startsWith(x))

    /** "Güterbock, Hans G." and "Hans G. Güterbock" are the same surname; a bracketed role or life span ("Ken Dowden
      * (ed.)", "Gimbutienė, Marija (1921-1994)") is not part of the name.
      */
    private[calibre] def surname(author: String): String =
      val name = author.replaceAll("\\([^)]*\\)", " ").trim
      norm(if name.contains(",") then name.takeWhile(_ != ',') else name.split("\\s+").last)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def authorsMatch(a: List[AuditRecord.Authors], b: List[Author]): Boolean =
      (a.map(x => surname(x.value)).toSet & b.map(x => surname(x.value)).toSet).exists(_.nonEmpty)

    /** Calibre's placeholder year for "unknown" is 101; anything before 1000 is not a real pubdate. */
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def year(b: Book): Option[Book.Year] =
      b.pubdate.flatMap(_.value.take(4).toIntOption).filter(_ >= 1000).map(y => Book.Year(y))

    /** The metadata a scrape or a bad import leaves: blank/Unknown, or a title that is a web page. */
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def junk(b: Book): Boolean =
      isBlank(b.title.value) || b.title.value.length > 300 || b.authors.isEmpty ||
        b.authors.exists(a => isBlank(a.value) || a.value.toLowerCase(java.util.Locale.ROOT).contains("unknown"))

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def issues(r: AuditRecord, b: Book): List[AuditIssue] =
      List(
        Option.when(junk(b))(AuditIssue.Junk),
        Option.when(!junk(b) && !titleMatches(r.title, b.title))(AuditIssue.Title),
        Option.when(!junk(b) && !authorsMatch(r.authors, b.authors))(AuditIssue.Authors),
        Option
          .when(r.year.isDefined && year(b).isDefined && r.year.map(_.value) != year(b).map(_.value))(AuditIssue.Year)
      ).flatten

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def audit(records: List[AuditRecord], books: Map[Book.Id, Book]): List[AuditFinding] =
      records.groupBy(_.calibreId).toList.sortBy(_._1).flatMap { (id, rs) =>
        val keys = rs.map(_.citekey).sorted.map(k => AuditFinding.Citekeys(k.value))
        books.get(Book.Id(id.value)) match
          case None =>
            List(
              AuditFinding(
                id,
                keys,
                List(AuditIssue.Missing),
                None,
                rs.minBy(_.citekey.value)
              )
            )
          case Some(b) =>
            val (best, found) = rs.map(r => r -> issues(r, b)).minBy((r, is) => (is.size, r.citekey.value))
            if found.isEmpty then Nil
            else List(AuditFinding(id, keys, found, Some(b), best))
      }

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
    /** Calibre writes `0101-01-01` for "no date"; a year below [[CalibreBook.FirstYear]] is that placeholder, not a
      * publication year.
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def year: Option[Book.Year] = pubdate
      .map(_.value.take(4))
      .filter(_.forall(_.isDigit))
      .flatMap(_.toIntOption)
      .filter(_ >= Book.FirstYear.value)
      .map(y => Book.Year(y))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def doi: Option[Push.Doi] =
      (identifiers.get(Book.IdentifiersKey("doi"))).map(refactorValue24 => Push.Doi((refactorValue24).value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def isbn: Option[Push.Isbn] =
      (identifiers.get(Book.IdentifiersKey("isbn"))).map(refactorValue25 => Push.Isbn((refactorValue25).value))

    /** The file to import: the requested format, else PDF, else EPUB (both carry a text layer). */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def pick(format: Option[CollectionFile.Format]): Option[Book.Formats] =
      val wanted = format.map(_.value.toLowerCase).toList ++ List("pdf", "epub")
      wanted.iterator.map(ext => formats.find((_).value.toLowerCase.endsWith("." + ext))).collectFirst { case Some(f) =>
        f
      }

  object Book:
    type Year = Year.T
    object Year extends Newtype[Int, Pure]

    type Id = Id.T
    object Id extends Newtype[Int, Pure]
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    export Calibre.Author as Authors
    type Pubdate = Pubdate.T
    object Pubdate extends Newtype[String, Pure]
    type Publisher = Publisher.T
    object Publisher extends Newtype[String, Pure]
    type Tags = Tags.T
    object Tags extends Newtype[String, Pure]
    type Comments = Comments.T
    object Comments extends Newtype[String, Pure]
    type IdentifiersKey = IdentifiersKey.T
    object IdentifiersKey extends Newtype[String, Pure]
    type IdentifiersValue = IdentifiersValue.T
    object IdentifiersValue extends Newtype[String, Pure]
    type Formats = Formats.T
    object Formats extends Newtype[String, Pure]
    type Cover = Cover.T
    object Cover extends Newtype[String, Pure]

    /** The first year a pubdate is taken as one: Calibre's "no date" is year 101, and the library cites nothing older
      * by its printing (the host's citekeys use the same bound).
      */
    val FirstYear: Year = Year(1000)

  /** One field `calibre update` changes: what Calibre has now and what it writes. */
  final case class Change(field: Field, from: Change.From, to: Change.To)

  object Change:
    type From = From.T
    object From extends Newtype[String, Pure]
    type To = To.T
    object To extends Newtype[String, Pure]

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
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    type Value = Value.T
    object Value extends Newtype[String, Pure]

  /** Where the library is and how it is reached, resolved from the plugin's settings: the library's folder (`~`
    * expanded), the `calibredb` and `sqlite3` to run, and the process whose presence means the Calibre GUI holds the
    * library.
    */
  final case class Library(
      library: Library.Value,
      calibredb: Settings.ExecutablePath,
      sqlite: Settings.Sqlite,
      guiProcess: Shell.ProcessMatch
  )

  object Library:
    type Value = Value.T
    object Value extends Newtype[String, Pure]

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
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    export Calibre.Author as Authors
    type Doi = Doi.T
    object Doi extends Newtype[String, Pure]
    type Isbn = Isbn.T
    object Isbn extends Newtype[String, Pure]
    export Book.Year
    type Publisher = Publisher.T
    object Publisher extends Newtype[String, Pure]
    type Summary = Summary.T
    object Summary extends Newtype[String, Pure]
    type Tags = Tags.T
    object Tags extends Newtype[String, Pure]

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
      libraryPath: Settings.LibraryPath,
      calibredb: Settings.Calibredb,
      sqlite: Settings.Sqlite,
      writeBack: Settings.WriteBack,
      guiProcess: Settings.GuiProcess
  ) derives ConfigReader:
    /** The settings as the library is reached, `~` read against `home`, a bare `calibredb` found on `searchPath` or in
      * Calibre's install directories (kept bare when it is in none, so the failure names it as configured).
      */
    def library(home: Option[EnvVar.Value], searchPath: Option[EnvVar.Value] = None)(using
        files: LocalFiles
    ): Library =
      library(home, searchPath, path => files.isExecutable(path))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def library(
        home: Option[EnvVar.Value],
        searchPath: Option[EnvVar.Value],
        executable: Path => Boolean
    ): Library =
      val path = libraryPath.value.trim match
        case "~"                                       => home.fold("~")(_.value)
        case p if p.startsWith("~/") && home.isDefined => home.fold("")(_.value) + p.drop(1)
        case p                                         => p
      Library(Library.Value(path), Settings.locate(calibredb, home, searchPath, executable), sqlite, guiProcess)

  object Settings:
    type ExecutablePath = ExecutablePath.T
    object ExecutablePath extends Newtype[String, Pure]

    /** Where Calibre's installers put `calibredb`: the macOS app (system-wide or the user's), Homebrew on Apple silicon
      * and Intel, and the Linux installer's `/opt/calibre`.
      */
    type InstallDir = InstallDir.T
    object InstallDir extends Newtype[String, Pure]

    type LibraryPath = LibraryPath.T
    object LibraryPath extends Newtype[String, Pure]
    type Calibredb = Calibredb.T
    object Calibredb extends Newtype[String, Pure]
    type Sqlite = Sqlite.T
    object Sqlite extends Newtype[String, Pure]
    export CalibreCollection.WriteBack
    export Shell.ProcessMatch as GuiProcess

    val InstallDirs: List[InstallDir] = List(
      InstallDir("/Applications/calibre.app/Contents/MacOS"),
      InstallDir("~/Applications/calibre.app/Contents/MacOS"),
      InstallDir("/opt/homebrew/bin"),
      InstallDir("/usr/local/bin"),
      InstallDir("/opt/calibre"),
      InstallDir("/usr/bin")
    )

    /** `name` as configured when it holds a `/`; otherwise the first executable `name` on `searchPath`, then in
      * [[InstallDirs]].
      */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def locate(
        name: Calibredb,
        home: Option[EnvVar.Value],
        searchPath: Option[EnvVar.Value],
        executable: Path => Boolean
    ): ExecutablePath =
      if name.value.contains('/') || name.value.isBlank then ExecutablePath(name.value)
      else
        val dirs = searchPath.toList.flatMap(_.value.split(':').toList).filter(_.nonEmpty) ++
          InstallDirs.flatMap(d =>
            if d.value.startsWith("~/") then home.map(_.value + d.value.drop(1)).toList else List(d.value)
          )
        ExecutablePath(dirs.iterator.map(d => Path.of(d, name.value)).find(executable).fold(name.value)(_.toString))
