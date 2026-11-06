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
final class Calibre(cfg: com.histmersion.plugins.calibre.Calibre.Library, shell: Shell)(using files: com.histmersion.plugin.LocalFiles):

  private def libArgs = List("--with-library", cfg.library)

  /** The Calibre GUI holds an exclusive lock on the library for writes. */
  def guiRunning: com.histmersion.plugins.calibre.Calibre.GuiRunningResult = shell.isRunning(cfg.guiProcess)

  def libraryExists: com.histmersion.plugins.calibre.Calibre.LibraryExistsResult = files.isFile(Path.of(cfg.library, "metadata.db"))

  // ── reading ─────────────────────────────────────────────────────────────────────────────────

  def search(query: com.histmersion.plugins.calibre.Calibre.SearchQuery, limit: com.histmersion.plugins.calibre.Calibre.SearchLimit = _root_.com.histmersion.plugins.calibre.Calibre.SearchLimit(20)): Either[com.histmersion.plugins.calibre.Calibre.SearchResultPart1, List[com.histmersion.plugins.calibre.Calibre.Book]] =
    viaCalibredb(Some(query), limit).orElse(viaSqlite(Some(query), None, limit))

  /** The whole library (read-only), for the audit: one listing instead of a call per entry. */
  def all(limit: com.histmersion.plugins.calibre.Calibre.AllLimit = _root_.com.histmersion.plugins.calibre.Calibre.AllLimit(1000000)): Either[com.histmersion.plugins.calibre.Calibre.AllResultPart1, List[com.histmersion.plugins.calibre.Calibre.Book]] =
    viaCalibredb(None, limit).orElse(viaSqlite(None, None, limit))

  def byId(id: com.histmersion.plugins.calibre.Calibre.ByIdId): Either[com.histmersion.plugins.calibre.Calibre.ByIdResultPart1, Option[com.histmersion.plugins.calibre.Calibre.Book]] =
    viaCalibredb(Some(_root_.com.histmersion.plugins.calibre.Calibre.ViaCalibredbQuery(s"id:$id")), _root_.com.histmersion.plugins.calibre.Calibre.ViaCalibredbLimit(1))
      .map(_.find(_.id == id))
      .orElse(viaSqlite(None, Some(id), 1).map(_.headOption))

  private def viaCalibredb(
      query: Option[com.histmersion.plugins.calibre.Calibre.ViaCalibredbQuery],
      limit: com.histmersion.plugins.calibre.Calibre.ViaCalibredbLimit
  ): Either[com.histmersion.plugins.calibre.Calibre.ViaCalibredbResultPart1, List[com.histmersion.plugins.calibre.Calibre.Book]] =
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
      query: Option[com.histmersion.plugins.calibre.Calibre.ViaSqliteQuery],
      id: Option[com.histmersion.plugins.calibre.Calibre.ViaSqliteId],
      limit: com.histmersion.plugins.calibre.Calibre.ViaSqliteLimit
  ): Either[com.histmersion.plugins.calibre.Calibre.ViaSqliteResultPart1, List[com.histmersion.plugins.calibre.Calibre.Book]] =
    val where = id match
      case Some(n) => s"b.id = ${(n).value.toInt}"
      case None    =>
        val like = Calibre.sqlLiteral(_root_.com.histmersion.plugins.calibre.Calibre.SqlLiteralS("%" + query.getOrElse("") + "%"))
        s"(b.title LIKE $like OR b.author_sort LIKE $like)"
    val sql = Calibre.sqliteQuery(_root_.com.histmersion.plugins.calibre.Calibre.SqliteQueryWhere(where), limit)
    shell
      .run(List(cfg.sqlite, _root_.com.histmersion.plugins.calibre.Shell.RunCmd("-readonly"), _root_.com.histmersion.plugins.calibre.Shell.RunCmd("-json"), Path.of(cfg.library, "metadata.db").toString, sql))
      .flatMap(Calibre.parseSqliteJson(_, cfg.library))

  // ── writing (push) ──────────────────────────────────────────────────────────────────────────

  /** `calibredb add` + `set_metadata` — the port of `to_calibre.py`. Returns the NEW Calibre id.
    *
    * The metadata guard is the point: Calibre entries with an empty or "Unknown" title/author are unfindable junk, and
    * the whole reason this pipeline pushes metadata at all is that Calibre's own metadata sources key on ISBN and
    * cannot identify a journal article.
    */
  def add(file: Path, meta: com.histmersion.plugins.calibre.Calibre.Push): Either[com.histmersion.plugins.calibre.Calibre.AddResultPart1, com.histmersion.plugins.calibre.Calibre.AddResultPart2] =
    for
      _ <- Calibre.guard(meta.title, meta.authors)
      _ <- Either.cond(
        !guiRunning,
        (),
        "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)"
      )
      _ <- Either.cond(files.isFile(file), (), s"no such file: $file")
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
      id: com.histmersion.plugins.calibre.Calibre.UpdateId,
      meta: com.histmersion.plugins.calibre.Calibre.Push
  ): Either[com.histmersion.plugins.calibre.Calibre.UpdateResultPart1, List[com.histmersion.plugins.calibre.Calibre.Change]] =
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
  def updateAll(linked: List[LinkedRecord], apply: com.histmersion.plugins.calibre.Calibre.UpdateAllApply): Either[com.histmersion.plugins.calibre.Calibre.UpdateAllResultPart1, List[UpdateEntry]] =
    for
      _ <- Either.cond(!(apply).value || !(guiRunning).value, (), GuiRunning)
      books <- all()
    yield Calibre.planAll(linked, books.map(b => b.id -> b).toMap).map { e =>
      e.outcome match
        case UpdateOutcome.Change(cs) if apply =>
          e.copy(outcome = write(e.calibreId, cs).fold(UpdateOutcome.Failed(cs, _), _ => UpdateOutcome.Applied(cs)))
        case _ => e
    }

  /** One `set_metadata` with the planned fields; nothing at all when there are none. */
  private def write(id: com.histmersion.plugins.calibre.Calibre.WriteId, changes: List[com.histmersion.plugins.calibre.Calibre.Change]): Either[com.histmersion.plugins.calibre.Calibre.WriteResultPart1, Unit] =
    if changes.isEmpty then Right(())
    else shell.run(Calibre.setMetadataCmd(cfg.calibredb, libArgs, id, changes)).map(_ => ())

  private val GuiRunning =
    "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)"

  /** Set named fields on an existing entry — the explicit, reviewed counterpart of [[update]] (`enrich-calibre` applies
    * approved fixes with it). Same guards as every other write.
    */
  def set(id: com.histmersion.plugins.calibre.Calibre.SetId, fields: List[(com.histmersion.plugins.calibre.Calibre.SetFieldsPart1, com.histmersion.plugins.calibre.Calibre.SetFieldsPart2)]): Either[com.histmersion.plugins.calibre.Calibre.SetResultPart1, Unit] =
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
  def replaceFormat(id: com.histmersion.plugins.calibre.Calibre.ReplaceFormatId, file: Path): Either[com.histmersion.plugins.calibre.Calibre.ReplaceFormatResultPart1, com.histmersion.plugins.calibre.Calibre.ReplaceFormatResultPart2] =
    for
      fmt <- Calibre.formatOf(file)
      _ <- Either.cond(
        !(guiRunning).value,
        (),
        "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)"
      )
      _ <- Either.cond(((files.isFile(file)) == _root_.com.histmersion.plugin.LocalFiles.IsFileResult(true)), (), s"no such file: $file")
      _ <- byId(id).flatMap(_.toRight(s"there is no Calibre entry $id"))
      _ <- shell.run(Calibre.replaceFormatCmd(cfg.calibredb, libArgs, id, file))
    yield fmt

  private def addCmd(file: Path, meta: com.histmersion.plugins.calibre.Calibre.Push): List[com.histmersion.plugins.calibre.Calibre.AddCmdResult] =
    List(cfg.calibredb, "add", file.toString) ++ libArgs ++
      List("--title", meta.title.trim, "--authors", meta.authors.mkString(" & "), "--duplicates") ++
      meta.doi.toList.flatMap(d => List("--identifier", s"doi:$d")) ++
      meta.isbn.toList.flatMap(i => List("--isbn", i)) ++
      (if meta.tags.isEmpty then Nil else List("--tags", meta.tags.mkString(",")))

  /** The fields `calibredb add` cannot set. Best-effort: a failure here leaves a usable entry. */
  private def setMetadata(id: com.histmersion.plugins.calibre.Calibre.SetMetadataId, meta: com.histmersion.plugins.calibre.Calibre.Push): Either[com.histmersion.plugins.calibre.Calibre.SetMetadataResultPart1, Unit] =
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
  type UpdatePlanEmptyV = UpdatePlanEmptyV.T
  object UpdatePlanEmptyV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatePlanChangeFrom = UpdatePlanChangeFrom.T
  object UpdatePlanChangeFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatePlanChangeTo = UpdatePlanChangeTo.T
  object UpdatePlanChangeTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeIdentifiersOkV = MergeIdentifiersOkV.T
  object MergeIdentifiersOkV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetCmdValueK = SetCmdValueK.T
  object SetCmdValueK extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSqliteJsonSF = ParseSqliteJsonSF.T
  object ParseSqliteJsonSF extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type GuiRunningResult = GuiRunningResult.T
  object GuiRunningResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LibraryExistsResult = LibraryExistsResult.T
  object LibraryExistsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchResultPart1 = SearchResultPart1.T
  object SearchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchQuery = SearchQuery.T
  object SearchQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllResultPart1 = AllResultPart1.T
  object AllResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllLimit = AllLimit.T
  object AllLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByIdResultPart1 = ByIdResultPart1.T
  object ByIdResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByIdId = ByIdId.T
  object ByIdId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViaCalibredbResultPart1 = ViaCalibredbResultPart1.T
  object ViaCalibredbResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViaCalibredbQuery = ViaCalibredbQuery.T
  object ViaCalibredbQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViaCalibredbLimit = ViaCalibredbLimit.T
  object ViaCalibredbLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViaSqliteResultPart1 = ViaSqliteResultPart1.T
  object ViaSqliteResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViaSqliteQuery = ViaSqliteQuery.T
  object ViaSqliteQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViaSqliteId = ViaSqliteId.T
  object ViaSqliteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ViaSqliteLimit = ViaSqliteLimit.T
  object ViaSqliteLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddResultPart1 = AddResultPart1.T
  object AddResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddResultPart2 = AddResultPart2.T
  object AddResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateResultPart1 = UpdateResultPart1.T
  object UpdateResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateId = UpdateId.T
  object UpdateId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateAllResultPart1 = UpdateAllResultPart1.T
  object UpdateAllResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdateAllApply = UpdateAllApply.T
  object UpdateAllApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteResultPart1 = WriteResultPart1.T
  object WriteResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteId = WriteId.T
  object WriteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetResultPart1 = SetResultPart1.T
  object SetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetId = SetId.T
  object SetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetFieldsPart1 = SetFieldsPart1.T
  object SetFieldsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetFieldsPart2 = SetFieldsPart2.T
  object SetFieldsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceFormatResultPart1 = ReplaceFormatResultPart1.T
  object ReplaceFormatResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceFormatResultPart2 = ReplaceFormatResultPart2.T
  object ReplaceFormatResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceFormatId = ReplaceFormatId.T
  object ReplaceFormatId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AddCmdResult = AddCmdResult.T
  object AddCmdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetMetadataResultPart1 = SetMetadataResultPart1.T
  object SetMetadataResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetMetadataId = SetMetadataId.T
  object SetMetadataId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsBlankResult = IsBlankResult.T
  object IsBlankResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsBlankS = IsBlankS.T
  object IsBlankS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardResultPart1 = GuardResultPart1.T
  object GuardResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GuardTitle = _root_.com.histmersion.plugins.calibre.Calibre.Push.Title
  val GuardTitle: _root_.com.histmersion.plugins.calibre.Calibre.Push.Title.type = _root_.com.histmersion.plugins.calibre.Calibre.Push.Title
  type GuardAuthors = GuardAuthors.T
  object GuardAuthors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceableFormats = ReplaceableFormats.T
  object ReplaceableFormats extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FormatOfResultPart1 = FormatOfResultPart1.T
  object FormatOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FormatOfResultPart2 = FormatOfResultPart2.T
  object FormatOfResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceFormatCmdResult = ReplaceFormatCmdResult.T
  object ReplaceFormatCmdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceFormatCmdCalibredb = ReplaceFormatCmdCalibredb.T
  object ReplaceFormatCmdCalibredb extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceFormatCmdLibArgs = ReplaceFormatCmdLibArgs.T
  object ReplaceFormatCmdLibArgs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReplaceFormatCmdId = ReplaceFormatCmdId.T
  object ReplaceFormatCmdId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpdatePlanResultPart1 = UpdatePlanResultPart1.T
  object UpdatePlanResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetMetadataCmdResult = SetMetadataCmdResult.T
  object SetMetadataCmdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetMetadataCmdCalibredb = _root_.com.histmersion.plugins.calibre.Calibre.Library.Calibredb
  val SetMetadataCmdCalibredb: _root_.com.histmersion.plugins.calibre.Calibre.Library.Calibredb.type = _root_.com.histmersion.plugins.calibre.Calibre.Library.Calibredb
  type SetMetadataCmdLibArgs = SetMetadataCmdLibArgs.T
  object SetMetadataCmdLibArgs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetMetadataCmdId = SetMetadataCmdId.T
  object SetMetadataCmdId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeIdentifiersResultPart1 = MergeIdentifiersResultPart1.T
  object MergeIdentifiersResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeIdentifiersResultPart2 = MergeIdentifiersResultPart2.T
  object MergeIdentifiersResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeIdentifiersCurrentPart1 = MergeIdentifiersCurrentPart1.T
  object MergeIdentifiersCurrentPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeIdentifiersCurrentPart2 = MergeIdentifiersCurrentPart2.T
  object MergeIdentifiersCurrentPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeIdentifiersDoi = MergeIdentifiersDoi.T
  object MergeIdentifiersDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MergeIdentifiersIsbn = MergeIdentifiersIsbn.T
  object MergeIdentifiersIsbn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentifiersValueResult = IdentifiersValueResult.T
  object IdentifiersValueResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentifiersValueIdsPart1 = IdentifiersValueIdsPart1.T
  object IdentifiersValueIdsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
  type IdentifiersValueIdsPart2 = IdentifiersValueIdsPart2.T
  object IdentifiersValueIdsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormaliseAuthorResult = NormaliseAuthorResult.T
  object NormaliseAuthorResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormaliseAuthorRaw = NormaliseAuthorRaw.T
  object NormaliseAuthorRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormaliseAuthorsResult = NormaliseAuthorsResult.T
  object NormaliseAuthorsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormaliseAuthorsAuthors = _root_.com.histmersion.plugins.calibre.Calibre.Push.Authors
  val NormaliseAuthorsAuthors: _root_.com.histmersion.plugins.calibre.Calibre.Push.Authors.type = _root_.com.histmersion.plugins.calibre.Calibre.Push.Authors
  type PlanAllBooksPart1 = PlanAllBooksPart1.T
  object PlanAllBooksPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettableFields = SettableFields.T
  object SettableFields extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetCmdResultPart1 = SetCmdResultPart1.T
  object SetCmdResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetCmdResultPart2 = SetCmdResultPart2.T
  object SetCmdResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetCmdCalibredb = _root_.com.histmersion.plugins.calibre.Calibre.Library.Calibredb
  val SetCmdCalibredb: _root_.com.histmersion.plugins.calibre.Calibre.Library.Calibredb.type = _root_.com.histmersion.plugins.calibre.Calibre.Library.Calibredb
  type SetCmdLibArgs = SetCmdLibArgs.T
  object SetCmdLibArgs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetCmdId = SetCmdId.T
  object SetCmdId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetCmdFieldsPart1 = SetCmdFieldsPart1.T
  object SetCmdFieldsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SetCmdFieldsPart2 = SetCmdFieldsPart2.T
  object SetCmdFieldsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAddedIdResult = ParseAddedIdResult.T
  object ParseAddedIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseAddedIdOut = ParseAddedIdOut.T
  object ParseAddedIdOut extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseMachineJsonResultPart1 = ParseMachineJsonResultPart1.T
  object ParseMachineJsonResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseMachineJsonOut = ParseMachineJsonOut.T
  object ParseMachineJsonOut extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSqliteJsonResultPart1 = ParseSqliteJsonResultPart1.T
  object ParseSqliteJsonResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSqliteJsonOut = ParseSqliteJsonOut.T
  object ParseSqliteJsonOut extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseSqliteJsonLibraryDir = ParseSqliteJsonLibraryDir.T
  object ParseSqliteJsonLibraryDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitAuthorsResult = SplitAuthorsResult.T
  object SplitAuthorsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SplitAuthorsS = SplitAuthorsS.T
  object SplitAuthorsS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SqlLiteralResult = SqlLiteralResult.T
  object SqlLiteralResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SqlLiteralS = SqlLiteralS.T
  object SqlLiteralS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SqliteQueryResult = SqliteQueryResult.T
  object SqliteQueryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SqliteQueryWhere = SqliteQueryWhere.T
  object SqliteQueryWhere extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SqliteQueryLimit = SqliteQueryLimit.T
  object SqliteQueryLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Blank = Set("", "unknown", "unknown author", "untitled", "n/a", "?", "none")

  def isBlank(s: com.histmersion.plugins.calibre.Calibre.IsBlankS): com.histmersion.plugins.calibre.Calibre.IsBlankResult = _root_.com.histmersion.plugins.calibre.Calibre.IsBlankResult(Blank.contains(Option((s).value).getOrElse("").trim.toLowerCase))

  /** The metadata guard, shared by `import` (Calibre → corpus) and `push` (corpus → Calibre). */
  def guard(title: com.histmersion.plugins.calibre.Calibre.GuardTitle, authors: List[com.histmersion.plugins.calibre.Calibre.GuardAuthors]): Either[com.histmersion.plugins.calibre.Calibre.GuardResultPart1, Unit] =
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
  val ReplaceableFormats: List[com.histmersion.plugins.calibre.Calibre.ReplaceableFormats] = List(_root_.com.histmersion.plugins.calibre.Calibre.ReplaceableFormats("pdf"), _root_.com.histmersion.plugins.calibre.Calibre.ReplaceableFormats("epub"))

  def formatOf(file: Path): Either[com.histmersion.plugins.calibre.Calibre.FormatOfResultPart1, com.histmersion.plugins.calibre.Calibre.FormatOfResultPart2] =
    val ext = file.getFileName.toString.reverse.takeWhile(_ != '.').reverse.toLowerCase
    Either.cond(
      ReplaceableFormats.contains(ext),
      _root_.com.histmersion.plugins.calibre.Calibre.FormatOfResultPart2(ext),
      _root_.com.histmersion.plugins.calibre.Calibre.FormatOfResultPart1(s"'${file.getFileName}' is not a ${ReplaceableFormats.mkString(" or ")} file")
    )

  /** `calibredb add_format` replaces the entry's existing format of the same type (that is its default;
    * `--dont-replace` would be the opposite).
    */
  def replaceFormatCmd(calibredb: com.histmersion.plugins.calibre.Calibre.ReplaceFormatCmdCalibredb, libArgs: List[com.histmersion.plugins.calibre.Calibre.ReplaceFormatCmdLibArgs], id: com.histmersion.plugins.calibre.Calibre.ReplaceFormatCmdId, file: Path): List[com.histmersion.plugins.calibre.Calibre.ReplaceFormatCmdResult] =
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
  ): Either[com.histmersion.plugins.calibre.Calibre.UpdatePlanResultPart1, List[com.histmersion.plugins.calibre.Calibre.Change]] =
    val authors = normaliseAuthors(meta.authors)
    guard(meta.title, authors).map { _ =>
      def empty(v: Option[com.histmersion.plugins.calibre.Calibre.UpdatePlanEmptyV]) = v.forall(s => isBlank(s) || s.trim == "-")
      def change(f: com.histmersion.plugins.calibre.Calibre.Field, from: com.histmersion.plugins.calibre.Calibre.UpdatePlanChangeFrom, to: com.histmersion.plugins.calibre.Calibre.UpdatePlanChangeTo) =
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
      calibredb: com.histmersion.plugins.calibre.Calibre.SetMetadataCmdCalibredb,
      libArgs: List[com.histmersion.plugins.calibre.Calibre.SetMetadataCmdLibArgs],
      id: com.histmersion.plugins.calibre.Calibre.SetMetadataCmdId,
      changes: List[com.histmersion.plugins.calibre.Calibre.Change]
  ): List[com.histmersion.plugins.calibre.Calibre.SetMetadataCmdResult] =
    List(calibredb, "set_metadata") ++ libArgs ++ List(id.toString) ++
      changes.flatMap(c => List("--field", s"${c.field.name}:${c.to}"))

  /** Calibre's identifiers with the record's merged in: the DOI is the record's (it names the work the corpus cites),
    * an ISBN only fills a gap, and nothing Calibre has is ever dropped. A DOI is case-insensitive, so one differing
    * only in case is left as Calibre has it. A value with a comma cannot cross `--field identifiers:` (the comma
    * separates the pairs), so it is left out.
    */
  def mergeIdentifiers(current: Map[com.histmersion.plugins.calibre.Calibre.MergeIdentifiersCurrentPart1, com.histmersion.plugins.calibre.Calibre.MergeIdentifiersCurrentPart2], doi: Option[com.histmersion.plugins.calibre.Calibre.MergeIdentifiersDoi], isbn: Option[com.histmersion.plugins.calibre.Calibre.MergeIdentifiersIsbn]): Map[com.histmersion.plugins.calibre.Calibre.MergeIdentifiersResultPart1, com.histmersion.plugins.calibre.Calibre.MergeIdentifiersResultPart2] =
    def ok(v: com.histmersion.plugins.calibre.Calibre.MergeIdentifiersOkV) = (v).value.trim.nonEmpty && !(v).value.contains(",")
    val sameDoi = (d: String) => current.get(_root_.com.histmersion.plugins.calibre.Calibre.MergeIdentifiersCurrentPart1("doi")).exists(_.trim.equalsIgnoreCase(d))
    current ++ doi.map((_).value.trim).filter(_root_.com.histmersion.plugins.calibre.Calibre.MergeIdentifiersOkV(ok)).filterNot(sameDoi).map("doi" -> _) ++
      isbn.map((_).value.trim).filter(_root_.com.histmersion.plugins.calibre.Calibre.MergeIdentifiersOkV(ok)).filterNot(_ => current.get(_root_.com.histmersion.plugins.calibre.Calibre.MergeIdentifiersCurrentPart1("isbn")).exists(_.trim.nonEmpty)).map("isbn" -> _)

  /** `type:value,type:value`, as `--field identifiers:` takes it, in a stable order. */
  def identifiersValue(ids: Map[com.histmersion.plugins.calibre.Calibre.IdentifiersValueIdsPart1, com.histmersion.plugins.calibre.Calibre.IdentifiersValueIdsPart2]): com.histmersion.plugins.calibre.Calibre.IdentifiersValueResult =
    _root_.com.histmersion.plugins.calibre.Calibre.IdentifiersValueResult(ids.toList.sortBy(_._1).map((k, v) => s"$k:$v").mkString(","))

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
  def normaliseAuthor(raw: com.histmersion.plugins.calibre.Calibre.NormaliseAuthorRaw): com.histmersion.plugins.calibre.Calibre.NormaliseAuthorResult =
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
    _root_.com.histmersion.plugins.calibre.Calibre.NormaliseAuthorResult(ordered.replaceAll("\\s+", " ").trim)

  /** [[normaliseAuthor]] over a list, dropping names that end up empty and repeats. */
  def normaliseAuthors(authors: List[com.histmersion.plugins.calibre.Calibre.NormaliseAuthorsAuthors]): List[com.histmersion.plugins.calibre.Calibre.NormaliseAuthorsResult] =
    authors.map(normaliseAuthor).filter(_.nonEmpty).distinct

  /** Plan `update --all`: one [[UpdateEntry]] per linked Calibre id, in id order. */
  def planAll(
      linked: List[LinkedRecord],
      books: Map[com.histmersion.plugins.calibre.Calibre.PlanAllBooksPart1, com.histmersion.plugins.calibre.Calibre.Book]
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
  val SettableFields: List[com.histmersion.plugins.calibre.Calibre.SettableFields] =
    List(_root_.com.histmersion.plugins.calibre.Calibre.SettableFields("title"), _root_.com.histmersion.plugins.calibre.Calibre.SettableFields("authors"), _root_.com.histmersion.plugins.calibre.Calibre.SettableFields("publisher"), _root_.com.histmersion.plugins.calibre.Calibre.SettableFields("pubdate"), _root_.com.histmersion.plugins.calibre.Calibre.SettableFields("languages"), _root_.com.histmersion.plugins.calibre.Calibre.SettableFields("identifiers"), _root_.com.histmersion.plugins.calibre.Calibre.SettableFields("tags"), _root_.com.histmersion.plugins.calibre.Calibre.SettableFields("comments"), _root_.com.histmersion.plugins.calibre.Calibre.SettableFields("series"))

  /** `calibredb set_metadata` for [[Calibre.set]], or why it is refused: an unknown field, nothing to set, or a
    * blank/"Unknown" title or author (the metadata guard).
    */
  def setCmd(
      calibredb: com.histmersion.plugins.calibre.Calibre.SetCmdCalibredb,
      libArgs: List[com.histmersion.plugins.calibre.Calibre.SetCmdLibArgs],
      id: com.histmersion.plugins.calibre.Calibre.SetCmdId,
      fields: List[(com.histmersion.plugins.calibre.Calibre.SetCmdFieldsPart1, com.histmersion.plugins.calibre.Calibre.SetCmdFieldsPart2)]
  ): Either[com.histmersion.plugins.calibre.Calibre.SetCmdResultPart1, List[com.histmersion.plugins.calibre.Calibre.SetCmdResultPart2]] =
    val unknown = fields.map(_._1).filterNot(SettableFields.contains)
    def value(k: com.histmersion.plugins.calibre.Calibre.SetCmdValueK) = fields.collectFirst { case (`k`, v) => v }
    for
      _ <- Either.cond(fields.nonEmpty, (), "nothing to set — pass --field <name>:<value>")
      _ <- Either.cond(
        unknown.isEmpty,
        (),
        s"cannot set ${unknown.mkString(", ")} — settable: ${SettableFields.mkString(", ")}"
      )
      _ <- value(_root_.com.histmersion.plugins.calibre.Calibre.SetCmdValueK("title")).fold(Right(()))(t => Either.cond(!isBlank(t), (), "refusing a blank or Unknown title"))
      _ <- value("authors").fold(Right(()))(a =>
        Either.cond(a.split("&").map(_.trim).forall(x => !isBlank(x)), (), "refusing a blank or Unknown author")
      )
    yield List(calibredb, "set_metadata") ++ libArgs ++ List(id.toString) ++
      fields.flatMap((k, v) => List("--field", s"$k:$v"))

  def parseAddedId(out: com.histmersion.plugins.calibre.Calibre.ParseAddedIdOut): Option[com.histmersion.plugins.calibre.Calibre.ParseAddedIdResult] =
    AddedIds.findFirstMatchIn((out).value).map(_.group(1)).flatMap(_.split(",").headOption).flatMap(_.trim.toIntOption)

  /** `calibredb list --for-machine`: authors as `A & B`, tags/formats as arrays, identifiers as an object, formats as
    * ABSOLUTE paths.
    */
  def parseMachineJson(out: com.histmersion.plugins.calibre.Calibre.ParseMachineJsonOut): Either[com.histmersion.plugins.calibre.Calibre.ParseMachineJsonResultPart1, List[com.histmersion.plugins.calibre.Calibre.Book]] =
    if (out).value.trim.isEmpty then Right(Nil) // no match: nothing is printed at all
    else
      io.circe.parser
        .parse((out).value)
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
      out: com.histmersion.plugins.calibre.Calibre.ParseSqliteJsonOut,
      libraryDir: com.histmersion.plugins.calibre.Calibre.ParseSqliteJsonLibraryDir
  ): Either[com.histmersion.plugins.calibre.Calibre.ParseSqliteJsonResultPart1, List[com.histmersion.plugins.calibre.Calibre.Book]] =
    // `sqlite3 -json` prints NOTHING for an empty result, not `[]` — that was "exhausted input"
    if (out).value.trim.isEmpty then Right(Nil)
    else
      io.circe.parser
        .parse((out).value)
        .left
        .map(e => s"sqlite3 returned unparseable JSON: ${e.getMessage}")
        .map(_.asArray.toList.flatten.flatMap { j =>
          val c = j.hcursor
          def s(f: com.histmersion.plugins.calibre.Calibre.ParseSqliteJsonSF) = c.downField(f).as[String].toOption.map(_.trim).filter(_.nonEmpty)
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

  private def splitAuthors(s: com.histmersion.plugins.calibre.Calibre.SplitAuthorsS): List[com.histmersion.plugins.calibre.Calibre.SplitAuthorsResult] =
    ((s).value.split(" & ").map(_.trim).filter(_.nonEmpty).toList).map(refactorValue8 => _root_.com.histmersion.plugins.calibre.Calibre.SplitAuthorsResult(refactorValue8))

  /** Single-quote a SQL literal for the `sqlite3` command line. The only user value that reaches SQL is a search term,
    * and it goes through here.
    */
  def sqlLiteral(s: com.histmersion.plugins.calibre.Calibre.SqlLiteralS): com.histmersion.plugins.calibre.Calibre.SqlLiteralResult = _root_.com.histmersion.plugins.calibre.Calibre.SqlLiteralResult("'" + (s).value.replace("'", "''") + "'")

  /** The read-only projection that mirrors `calibredb --for-machine` (Calibre's schema: `books` plus link tables for
    * authors/tags/publishers/identifiers, and `data` for the files).
    */
  def sqliteQuery(where: com.histmersion.plugins.calibre.Calibre.SqliteQueryWhere, limit: com.histmersion.plugins.calibre.Calibre.SqliteQueryLimit): com.histmersion.plugins.calibre.Calibre.SqliteQueryResult =
    _root_.com.histmersion.plugins.calibre.Calibre.SqliteQueryResult(s"""SELECT b.id AS id, b.title AS title,
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
       |FROM books b WHERE $where ORDER BY b.id DESC LIMIT ${(limit).value.max(1)}""".stripMargin
      .replace("\n", " "))

  /** The PURE half of `histmersion calibre audit`: compare every linked entry with its record. */
  object Audit:

    /** Lower-case ASCII words: accents, punctuation and spacing are not disagreements. */
    type NormResult = NormResult.T
    object NormResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NormS = NormS.T
    object NormS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TitleMatchesResult = TitleMatchesResult.T
    object TitleMatchesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TitleMatchesA = TitleMatchesA.T
    object TitleMatchesA extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TitleMatchesB = TitleMatchesB.T
    object TitleMatchesB extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SurnameResult = SurnameResult.T
    object SurnameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SurnameAuthor = SurnameAuthor.T
    object SurnameAuthor extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AuthorsMatchResult = AuthorsMatchResult.T
    object AuthorsMatchResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AuthorsMatchA = AuthorsMatchA.T
    object AuthorsMatchA extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AuthorsMatchB = AuthorsMatchB.T
    object AuthorsMatchB extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type YearResult = YearResult.T
    object YearResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type JunkResult = JunkResult.T
    object JunkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AuditBooksPart1 = AuditBooksPart1.T
    object AuditBooksPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def norm(s: com.histmersion.plugins.calibre.Calibre.Audit.NormS): com.histmersion.plugins.calibre.Calibre.Audit.NormResult =
      Normalizer
        .normalize((s).value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(java.util.Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim

    /** Equal after [[norm]], or one extends the other: Calibre often keeps or drops a subtitle. */
    def titleMatches(a: com.histmersion.plugins.calibre.Calibre.Audit.TitleMatchesA, b: com.histmersion.plugins.calibre.Calibre.Audit.TitleMatchesB): com.histmersion.plugins.calibre.Calibre.Audit.TitleMatchesResult =
      val (x, y) = (norm(a), norm(b))
      x.nonEmpty && y.nonEmpty && (x.startsWith(y) || y.startsWith(x))

    /** "Güterbock, Hans G." and "Hans G. Güterbock" are the same surname; a bracketed role or life span ("Ken Dowden
      * (ed.)", "Gimbutienė, Marija (1921-1994)") is not part of the name.
      */
    def surname(author: com.histmersion.plugins.calibre.Calibre.Audit.SurnameAuthor): com.histmersion.plugins.calibre.Calibre.Audit.SurnameResult =
      val name = (author).value.replaceAll("\\([^)]*\\)", " ").trim
      norm(if name.contains(",") then _root_.com.histmersion.plugins.calibre.Calibre.Audit.NormS(name.takeWhile(_ != ',')) else _root_.com.histmersion.plugins.calibre.Calibre.Audit.NormS(name.split("\\s+").last))

    def authorsMatch(a: List[com.histmersion.plugins.calibre.Calibre.Audit.AuthorsMatchA], b: List[com.histmersion.plugins.calibre.Calibre.Audit.AuthorsMatchB]): com.histmersion.plugins.calibre.Calibre.Audit.AuthorsMatchResult =
      (a.map(surname).toSet & b.map(surname).toSet).exists(_.nonEmpty)

    /** Calibre's placeholder year for "unknown" is 101; anything before 1000 is not a real pubdate. */
    def year(b: Book): Option[com.histmersion.plugins.calibre.Calibre.Audit.YearResult] =
      b.pubdate.flatMap(_.take(4).toIntOption).filter(_ >= 1000)

    /** The metadata a scrape or a bad import leaves: blank/Unknown, or a title that is a web page. */
    def junk(b: Book): com.histmersion.plugins.calibre.Calibre.Audit.JunkResult =
      Calibre.isBlank(b.title) || b.title.length > 300 || b.authors.isEmpty ||
        b.authors.exists(a => Calibre.isBlank(a) || a.toLowerCase(java.util.Locale.ROOT).contains("unknown"))

    def issues(r: AuditRecord, b: Book): List[AuditIssue] =
      List(
        Option.when(((junk(b)) == _root_.com.histmersion.plugins.calibre.Calibre.Audit.JunkResult(true)))(AuditIssue.Junk),
        Option.when(!(junk(b)).value && !titleMatches(r.title, b.title))(AuditIssue.Title),
        Option.when(!(junk(b)).value && !authorsMatch(r.authors, b.authors))(AuditIssue.Authors),
        Option.when(r.year.isDefined && year(b).isDefined && r.year != year(b))(AuditIssue.Year)
      ).flatten

    def audit(records: List[AuditRecord], books: Map[com.histmersion.plugins.calibre.Calibre.Audit.AuditBooksPart1, Book]): List[AuditFinding] =
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
    def year: Option[com.histmersion.plugins.calibre.Calibre.Book.YearResult] = pubdate
      .map((_).value.take(4))
      .filter(_.forall(_.isDigit))
      .flatMap(_.toIntOption)
      .filter(_ >= Book.FirstYear)
    def doi: Option[com.histmersion.plugins.calibre.Calibre.Book.DoiResult] = identifiers.get(_root_.com.histmersion.plugins.calibre.Calibre.Book.IdentifiersKey("doi"))
    def isbn: Option[com.histmersion.plugins.calibre.Calibre.Book.IsbnResult] = identifiers.get(_root_.com.histmersion.plugins.calibre.Calibre.Book.IdentifiersKey("isbn"))

    /** The file to import: the requested format, else PDF, else EPUB (both carry a text layer). */
    def pick(format: Option[com.histmersion.plugins.calibre.Calibre.Book.PickFormat]): Option[com.histmersion.plugins.calibre.Calibre.Book.PickResult] =
      val wanted = format.map((_).value.toLowerCase).toList ++ List("pdf", "epub")
      wanted.iterator.map(ext => formats.find((_).value.toLowerCase.endsWith("." + ext))).collectFirst { case Some(f) =>
        f
      }

  object Book:
    /** The first year a pubdate is taken as one: Calibre's "no date" is year 101, and the library cites nothing older
      * by its printing (the host's citekeys use the same bound).
      */
    type YearResult = YearResult.T
    object YearResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoiResult = DoiResult.T
    object DoiResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsbnResult = IsbnResult.T
    object IsbnResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PickResult = PickResult.T
    object PickResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PickFormat = PickFormat.T
    object PickFormat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FirstYear = FirstYear.T
    object FirstYear extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pubdate = Pubdate.T
    object Pubdate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Publisher = Publisher.T
    object Publisher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Comments = Comments.T
    object Comments extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IdentifiersKey = IdentifiersKey.T
    object IdentifiersKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IdentifiersValue = IdentifiersValue.T
    object IdentifiersValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Formats = Formats.T
    object Formats extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Cover = Cover.T
    object Cover extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val FirstYear: com.histmersion.plugins.calibre.Calibre.Book.FirstYear = 1000

  /** One field `calibre update` changes: what Calibre has now and what it writes. */
  final case class Change(field: Field, from: Change.From, to: Change.To)

  object Change:
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A field `calibre update` writes, by its `calibredb set_metadata --field` name. */
  enum Field(val name: Field.Name):
    case Title extends Field(_root_.com.histmersion.plugins.calibre.Calibre.Field.Name("title"))
    case Authors extends Field(_root_.com.histmersion.plugins.calibre.Calibre.Field.Name("authors"))
    case Pubdate extends Field(_root_.com.histmersion.plugins.calibre.Calibre.Field.Name("pubdate"))
    case Identifiers extends Field(_root_.com.histmersion.plugins.calibre.Calibre.Field.Name("identifiers"))
    case Tags extends Field(_root_.com.histmersion.plugins.calibre.Calibre.Field.Name("tags"))
    case Publisher extends Field(_root_.com.histmersion.plugins.calibre.Calibre.Field.Name("publisher"))
    case Comments extends Field(_root_.com.histmersion.plugins.calibre.Calibre.Field.Name("comments"))

  object Field:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Where the library is and how it is reached, resolved from the plugin's settings: the library's folder (`~`
    * expanded), the `calibredb` and `sqlite3` to run, and the process whose presence means the Calibre GUI holds the
    * library.
    */
  final case class Library(library: Library.Value, calibredb: Library.Calibredb, sqlite: Library.Sqlite, guiProcess: Library.GuiProcess)

  object Library:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Calibredb = Calibredb.T
    object Calibredb extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sqlite = Sqlite.T
    object Sqlite extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GuiProcess = GuiProcess.T
    object GuiProcess extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Doi = Doi.T
    object Doi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Isbn = Isbn.T
    object Isbn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Year = Year.T
    object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Publisher = Publisher.T
    object Publisher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Summary = Summary.T
    object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    def library(home: Option[com.histmersion.plugins.calibre.Calibre.Settings.LibraryHome], searchPath: Option[com.histmersion.plugins.calibre.Calibre.Settings.LibrarySearchPath] = None)(using files: com.histmersion.plugin.LocalFiles): Library =
      library(home, searchPath, files.isExecutable)

    def library(
        home: Option[com.histmersion.plugins.calibre.Calibre.Settings.LibraryHome],
        searchPath: Option[com.histmersion.plugins.calibre.Calibre.Settings.LibrarySearchPath],
        executable: Path => com.histmersion.plugins.calibre.Calibre.Settings.LibraryExecutableResult
    ): Library =
      val path = (libraryPath).value.trim match
        case "~"                                       => home.getOrElse("~")
        case p if p.startsWith("~/") && home.isDefined => home.getOrElse("") + p.drop(1)
        case p                                         => p
      Library(path, Settings.locate(calibredb, home, searchPath, executable), sqlite, guiProcess)

  object Settings:

    /** Where Calibre's installers put `calibredb`: the macOS app (system-wide or the user's), Homebrew on Apple silicon
      * and Intel, and the Linux installer's `/opt/calibre`.
      */
    type LibraryHome = LibraryHome.T
    object LibraryHome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LibrarySearchPath = LibrarySearchPath.T
    object LibrarySearchPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LibraryExecutableResult = LibraryExecutableResult.T
    object LibraryExecutableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InstallDirs = InstallDirs.T
    object InstallDirs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LocateResult = LocateResult.T
    object LocateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LocateName = LocateName.T
    object LocateName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LocateHome = LocateHome.T
    object LocateHome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LocateSearchPath = LocateSearchPath.T
    object LocateSearchPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LocateExecutableResult = LocateExecutableResult.T
    object LocateExecutableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type LibraryPath = LibraryPath.T
    object LibraryPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Calibredb = Calibredb.T
    object Calibredb extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sqlite = Sqlite.T
    object Sqlite extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WriteBack = WriteBack.T
    object WriteBack extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GuiProcess = GuiProcess.T
    object GuiProcess extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val InstallDirs: List[com.histmersion.plugins.calibre.Calibre.Settings.InstallDirs] = List(
      _root_.com.histmersion.plugins.calibre.Calibre.Settings.InstallDirs("/Applications/calibre.app/Contents/MacOS"),
      _root_.com.histmersion.plugins.calibre.Calibre.Settings.InstallDirs("~/Applications/calibre.app/Contents/MacOS"),
      _root_.com.histmersion.plugins.calibre.Calibre.Settings.InstallDirs("/opt/homebrew/bin"),
      _root_.com.histmersion.plugins.calibre.Calibre.Settings.InstallDirs("/usr/local/bin"),
      _root_.com.histmersion.plugins.calibre.Calibre.Settings.InstallDirs("/opt/calibre"),
      _root_.com.histmersion.plugins.calibre.Calibre.Settings.InstallDirs("/usr/bin")
    )

    /** `name` as configured when it holds a `/`; otherwise the first executable `name` on `searchPath`, then in
      * [[InstallDirs]].
      */
    def locate(name: com.histmersion.plugins.calibre.Calibre.Settings.LocateName, home: Option[com.histmersion.plugins.calibre.Calibre.Settings.LocateHome], searchPath: Option[com.histmersion.plugins.calibre.Calibre.Settings.LocateSearchPath], executable: Path => com.histmersion.plugins.calibre.Calibre.Settings.LocateExecutableResult): com.histmersion.plugins.calibre.Calibre.Settings.LocateResult =
      if (name).value.contains('/') || (name).value.isBlank then name
      else
        val dirs = searchPath.toList.flatMap((_).value.split(':').toList).filter(_.nonEmpty) ++
          InstallDirs.flatMap(d => if d.startsWith("~/") then home.map((_).value + d.drop(1)).toList else List(d))
        dirs.iterator.map(d => Path.of(d, (name).value)).find({ val refactorFunction4 = executable; (refactorValue21) => ((refactorFunction4(refactorValue21)) == _root_.com.histmersion.plugins.calibre.Calibre.Settings.LocateExecutableResult(true)) }).fold(name)(_.toString)
