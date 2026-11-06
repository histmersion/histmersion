package com.histmersion.plugins.calibre

import com.histmersion.plugin.*

import java.nio.file.{Files, Path}
import scala.util.Try

/** The Calibre library as a [[SourceCollection]]: [[Calibre]] read and written, its books as collection items. Writes
  * only with `writeBack`, and then refused while the Calibre GUI holds the library.
  */
final class CalibreCollection(library: Calibre.Library, writeBack: CalibreCollection.WriteBack, shell: Shell)(using files: com.histmersion.plugin.LocalFiles) extends SourceCollection:
  private val calibre = Calibre(library, shell)

  val title: com.histmersion.plugins.calibre.CalibreCollection.Title = _root_.com.histmersion.plugins.calibre.CalibreCollection.Title("Calibre")
  override val description: com.histmersion.plugins.calibre.CalibreCollection.Description =
    s"The Calibre library at ${library.library}: read with calibredb (sqlite3 while the GUI holds it)" +
      (if writeBack then ", written back with calibredb" else ", read-only")

  override def capabilities: Set[CollectionCapability] =
    if writeBack then Set(CollectionCapability.Read, CollectionCapability.Add, CollectionCapability.Update)
    else Set(CollectionCapability.Read)

  override def readOnlyReason: String =
    "write-back is off — set histmersion.plugins.calibre.write-back = true " +
      "(HISTMERSION_PLUGIN_CALIBRE_WRITE_BACK=true) to let the server write to the library"

  override def reach: Either[String, Unit] = CalibreCollection.reach(Path.of(library.library))

  def search(query: CollectionQuery): Either[com.histmersion.plugins.calibre.CalibreCollection.SearchResultPart1, List[CollectionItem]] =
    (query.text match
      case Some(text) => calibre.search(text, query.limit.getOrElse(CalibreCollection.SearchLimit))
      case None       => query.limit.fold(calibre.all())(calibre.all(_))
    ).map(_.map(CalibreCollection.item))

  def item(id: CollectionId): Either[com.histmersion.plugins.calibre.CalibreCollection.ItemResultPart1, Option[CollectionItem]] =
    id.value.trim.toIntOption.fold(Right(None))(calibre.byId(_).map(_.map(CalibreCollection.item)))

  override def admits(item: CollectionItem): Either[String, Unit] =
    Calibre.guard(item.title.getOrElse(""), item.authors).left.map(e => s"Calibre id ${item.id.value} has $e")

  def file(item: CollectionItem, format: Option[com.histmersion.plugins.calibre.CalibreCollection.FileFormat]): Either[com.histmersion.plugins.calibre.CalibreCollection.FileResultPart1, CollectionFile] =
    CalibreCollection
      .book(item)
      .pick(format)
      .map(CollectionFile(_))
      .toRight(
        s"Calibre id ${item.id.value} has no pdf/epub (formats: ${item.files.map(_.name).mkString(", ")}); " +
          "KFX/MOBI must be converted in Calibre first"
      )

  def original(item: CollectionItem, file: CollectionFile): Either[com.histmersion.plugins.calibre.CalibreCollection.OriginalResultPart1, FetchedFile] =
    CalibreCollection
      .read(file.name)
      .map(bytes => FetchedFile("calibre", Some(file.name), bytes, CalibreCollection.extension(file.name)))

  override def cover(item: CollectionItem): Either[String, Option[FetchedFile]] =
    item.cover.map(_.trim).filter(p => p.nonEmpty && files.isFile(Path.of(p))) match
      case None       => Right(None)
      case Some(path) =>
        CalibreCollection
          .read(path)
          .map(b => Some(FetchedFile(_root_.com.histmersion.plugin.FetchedFile.Source("calibre"), Some(path), b, CalibreCollection.extension(path))))

  override def add(metadata: CollectionMetadata, file: FetchedFile): Either[String, CollectionId] =
    CalibreCollection
      .staged(metadata.key, file)(p => calibre.add(p, CalibreCollection.push(metadata)))
      .map(id => CollectionId(id.toString))

  override def replace(id: CollectionId, key: String, file: FetchedFile): Either[String, String] =
    CalibreCollection.number(id).flatMap(n => CalibreCollection.staged(_root_.com.histmersion.plugins.calibre.CalibreCollection.StagedName(key), file)(p => calibre.replaceFormat(n, p)))

  override def update(id: CollectionId, metadata: CollectionMetadata): Either[String, List[CollectionChange]] =
    CalibreCollection
      .number(id)
      .flatMap(calibre.update(_, CalibreCollection.push(metadata)))
      .map(_.map(CalibreCollection.change))

  override def updateAll(links: List[CollectionLink], apply: Boolean): Either[String, List[CollectionUpdateEntry]] =
    val linked = links.flatMap(l =>
      l.id.value.trim.toIntOption.map(LinkedRecord(l.metadata.key, _root_.com.histmersion.plugins.calibre.LinkedRecord.CalibreId(_), CalibreCollection.push(l.metadata)))
    )
    calibre.updateAll(linked, apply).map(_.map(CalibreCollection.entry))

  override def set(id: CollectionId, fields: List[(String, String)]): Either[String, Unit] =
    CalibreCollection.number(id).flatMap(calibre.set(_, (fields).map(refactorValue => { val refactorTuple = refactorValue; (_root_.com.histmersion.plugins.calibre.Calibre.SetFieldsPart1(refactorTuple._1), _root_.com.histmersion.plugins.calibre.Calibre.SetFieldsPart2(refactorTuple._2)) })))

  override def audit(records: List[CollectionRecord]): Either[String, List[CollectionFinding]] =
    val recs = records.flatMap(r =>
      r.id.value.trim.toIntOption.map(AuditRecord(r.key, _root_.com.histmersion.plugins.calibre.AuditRecord.CalibreId(_), r.title, r.authors, r.year.map(_.value)))
    )
    calibre.all().map { books =>
      Calibre.Audit.audit(recs, books.map(b => b.id -> b).toMap).map(CalibreCollection.finding)
    }

object CalibreCollection:
  /** The most books a search answers when the host does not say. */
  type ReadResultPart2 = ReadResultPart2.T
  object ReadResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type WriteBack = WriteBack.T
  object WriteBack extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchResultPart1 = SearchResultPart1.T
  object SearchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemResultPart1 = ItemResultPart1.T
  object ItemResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileResultPart1 = FileResultPart1.T
  object FileResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileFormat = FileFormat.T
  object FileFormat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OriginalResultPart1 = OriginalResultPart1.T
  object OriginalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchLimit = SearchLimit.T
  object SearchLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NumberResultPart1 = NumberResultPart1.T
  object NumberResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NumberResultPart2 = NumberResultPart2.T
  object NumberResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtensionResult = ExtensionResult.T
  object ExtensionResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExtensionName = ExtensionName.T
  object ExtensionName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadResultPart1 = ReadResultPart1.T
  object ReadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadPath = ReadPath.T
  object ReadPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StagedResultPart1 = StagedResultPart1.T
  object StagedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StagedName = StagedName.T
  object StagedName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StagedFResultPart1 = StagedFResultPart1.T
  object StagedFResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReachResultPart1 = ReachResultPart1.T
  object ReachResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val SearchLimit: com.histmersion.plugins.calibre.CalibreCollection.SearchLimit = 20

  /** A book as a collection item. */
  def item(b: Calibre.Book): CollectionItem =
    CollectionItem(
      id = CollectionId(b.id.toString),
      title = Some(b.title),
      authors = b.authors,
      year = b.year.map(Year(_)),
      published = b.pubdate,
      publisher = b.publisher,
      description = b.comments,
      tags = b.tags,
      identifiers = b.identifiers,
      files = b.formats.map(CollectionFile(_)),
      cover = b.cover
    )

  /** An item back as the book it was read from. */
  def book(i: CollectionItem): Calibre.Book =
    Calibre.Book(
      id = _root_.com.histmersion.plugins.calibre.Calibre.Book.Id(i.id.value.trim.toIntOption.getOrElse(0)),
      title = i.title.getOrElse(""),
      authors = i.authors,
      pubdate = i.published,
      publisher = i.publisher,
      tags = i.tags,
      comments = i.description,
      identifiers = i.identifiers,
      formats = i.files.map(_.name),
      cover = i.cover
    )

  def push(m: CollectionMetadata): Calibre.Push =
    Calibre.Push(m.title, m.authors, m.doi, m.isbn, m.year.map(_.value), m.publisher, m.summary, m.tags)

  def change(c: Calibre.Change): CollectionChange = CollectionChange(c.field.name, c.from, c.to)

  def entry(e: UpdateEntry): CollectionUpdateEntry =
    def all(cs: List[Calibre.Change]) = cs.map(change)
    val outcome = e.outcome match
      case UpdateOutcome.Change(cs)         => CollectionUpdateOutcome.Change(all(cs))
      case UpdateOutcome.Applied(cs)        => CollectionUpdateOutcome.Applied(all(cs))
      case UpdateOutcome.Failed(cs, reason) => CollectionUpdateOutcome.Failed(all(cs), reason)
      case UpdateOutcome.UpToDate           => CollectionUpdateOutcome.UpToDate
      case UpdateOutcome.Shared             => CollectionUpdateOutcome.Shared
      case UpdateOutcome.Missing            => CollectionUpdateOutcome.Missing
      case UpdateOutcome.Refused(reason)    => CollectionUpdateOutcome.Refused(reason)
    CollectionUpdateEntry(CollectionId(e.calibreId.toString), e.citekeys, outcome)

  def finding(f: AuditFinding): CollectionFinding =
    CollectionFinding(
      CollectionId(f.calibreId.toString),
      f.citekeys,
      f.issues.map(i => CollectionIssue(_root_.com.histmersion.plugin.CollectionIssue.Code(i.toString), i.label)),
      f.calibre.map(item),
      CollectionRecord(
        f.record.citekey,
        CollectionId(f.record.calibreId.toString),
        f.record.title,
        f.record.authors,
        f.record.year.map(Year(_))
      ),
      f.fixable
    )

  def number(id: CollectionId): Either[com.histmersion.plugins.calibre.CalibreCollection.NumberResultPart1, com.histmersion.plugins.calibre.CalibreCollection.NumberResultPart2] =
    ((id.value.trim.toIntOption.toRight(s"'${id.value}' is not a Calibre id")).left.map(refactorError => _root_.com.histmersion.plugins.calibre.CalibreCollection.NumberResultPart1(refactorError))).map(refactorValue24 => _root_.com.histmersion.plugins.calibre.CalibreCollection.NumberResultPart2(refactorValue24))

  def extension(name: com.histmersion.plugins.calibre.CalibreCollection.ExtensionName): com.histmersion.plugins.calibre.CalibreCollection.ExtensionResult = name.reverse.takeWhile(_ != '.').reverse.toLowerCase(java.util.Locale.ROOT)

  def read(path: com.histmersion.plugins.calibre.CalibreCollection.ReadPath)(using files: com.histmersion.plugin.LocalFiles): Either[com.histmersion.plugins.calibre.CalibreCollection.ReadResultPart1, com.histmersion.plugins.calibre.CalibreCollection.ReadResultPart2] =
    Try(files.read(Path.of(path))).toEither.left.map(e => s"cannot read $path (${e.getMessage})")

  /** `file` written as `<a temporary directory>/<name>.<extension>` (the extension is what tells Calibre the format),
    * `f` run on it, and the directory deleted whatever happens.
    */
  def staged[A](name: com.histmersion.plugins.calibre.CalibreCollection.StagedName, file: FetchedFile)(f: Path => Either[com.histmersion.plugins.calibre.CalibreCollection.StagedFResultPart1, A])(using files: com.histmersion.plugin.LocalFiles): Either[com.histmersion.plugins.calibre.CalibreCollection.StagedResultPart1, A] =
    files.staged(_root_.com.histmersion.plugin.LocalFiles.StagedName(s"$name.${file.extension}"), file.bytes)(f)

  /** Whether the server can read the library at `dir`, and when it cannot, what it needs. */
  def reach(dir: Path)(using files: com.histmersion.plugin.LocalFiles): Either[com.histmersion.plugins.calibre.CalibreCollection.ReachResultPart1, Unit] =
    val db = dir.resolve("metadata.db")
    if !files.isDirectory(dir) && !files.exists(db) then
      Left(
        _root_.com.histmersion.plugins.calibre.CalibreCollection.ReachResultPart1(s"there is no Calibre library at $dir — set histmersion.plugins.calibre.library-path to the folder that " +
          "holds metadata.db (a server in a container needs that folder mounted)")
      )
    else
      Try { files.readPrefix(db, 16); () }.toEither.left
        .map {
          case _: java.nio.file.NoSuchFileException =>
            s"there is no metadata.db in $dir — set histmersion.plugins.calibre.library-path to the Calibre library's folder"
          case e =>
            s"the server cannot read $db (${e.getClass.getSimpleName}: ${e.getMessage}) — on macOS a library in " +
              "Documents, Desktop, Downloads, iCloud Drive or on an external volume needs Full Disk Access (or Files " +
              "and Folders) for the server's binary, granted in System Settings → Privacy & Security; a server in a " +
              "container needs the library mounted"
        }
        .map(_ => ())
