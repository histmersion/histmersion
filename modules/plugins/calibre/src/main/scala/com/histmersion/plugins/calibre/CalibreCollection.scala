package com.histmersion
package plugins.calibre

import com.histmersion.plugin.*

import java.nio.file.Path
import scala.util.Try
import io.github.iltotore.iron.constraint.any.Pure

/** The Calibre library as a [[SourceCollection]]: [[Calibre]] read and written, its books as collection items. Writes
  * only with `writeBack`, and then refused while the Calibre GUI holds the library.
  */
final class CalibreCollection(library: Calibre.Library, writeBack: CalibreCollection.WriteBack, shell: Shell)(using
    files: LocalFiles
) extends SourceCollection:
  private val calibre = Calibre(library, shell)

  val title: SourceCollection.TitleResult = SourceCollection.TitleResult("Calibre")
  override val description: SourceCollection.DescriptionResult =
    SourceCollection.DescriptionResult(
      s"The Calibre library at ${library.library.value}: read with calibredb (sqlite3 while the GUI holds it)" +
        (if (writeBack).value then ", written back with calibredb" else ", read-only")
    )

  override def capabilities: Set[CollectionCapability] =
    if (writeBack).value then Set(CollectionCapability.Read, CollectionCapability.Add, CollectionCapability.Update)
    else Set(CollectionCapability.Read)

  override def readOnlyReason: SourceCollection.ReadOnlyReasonResult =
    SourceCollection.ReadOnlyReasonResult(
      "write-back is off — set histmersion.plugins.calibre.write-back = true " +
        "(HISTMERSION_PLUGIN_CALIBRE_WRITE_BACK=true) to let the server write to the library"
    )

  override def reach: Either[SourceCollection.ReachResultPart1, Unit] =
    CalibreCollection.reach(Path.of(library.library.value)).left.map(e => SourceCollection.ReachResultPart1(e.value))

  def search(query: CollectionQuery): Either[SourceCollection.SearchResultPart1, List[CollectionItem]] =
    (query.text match
      case Some(text) =>
        calibre.search(text, query.limit.fold(CalibreCollection.SearchLimit)(l => Calibre.SearchLimit(l.value)))
      case None => query.limit.fold(calibre.all())(l => calibre.all(Calibre.AllLimit(l.value)))
    ).map(_.map(CalibreCollection.item)).left.map(e => SourceCollection.SearchResultPart1(e.value))

  def item(id: CollectionId): Either[SourceCollection.ItemResultPart1, Option[CollectionItem]] =
    id.value.trim.toIntOption
      .fold[Either[Calibre.Problem, Option[CollectionItem]]](Right(None))(n =>
        calibre.byId(Calibre.Book.Id(n)).map(_.map(CalibreCollection.item))
      )
      .left
      .map(e => SourceCollection.ItemResultPart1(e.value))

  override def admits(item: CollectionItem): Either[SourceCollection.AdmitsResultPart1, Unit] =
    Calibre
      .guard(Calibre.Push.Title(item.title.fold("")(_.value)), item.authors.map(a => Calibre.Push.Authors(a.value)))
      .left
      .map(e => SourceCollection.AdmitsResultPart1(s"Calibre id ${item.id.value} has ${e.value}"))

  def file(
      item: CollectionItem,
      format: Option[SourceCollection.FileFormat]
  ): Either[SourceCollection.FileResultPart1, CollectionFile] =
    CalibreCollection
      .book(item)
      .pick(format.map(f => CollectionFile.Format(f.value)))
      .map(f => CollectionFile(CollectionFile.Name(f.value)))
      .toRight(
        SourceCollection.FileResultPart1(
          s"Calibre id ${item.id.value} has no pdf/epub (formats: ${item.files.map(_.name.value).mkString(", ")}); " +
            "KFX/MOBI must be converted in Calibre first"
        )
      )

  def original(item: CollectionItem, file: CollectionFile): Either[SourceCollection.OriginalResultPart1, FetchedFile] =
    CalibreCollection
      .read(file.name)
      .map(bytes =>
        FetchedFile(
          FetchedFile.Source("calibre"),
          Some(FetchedFile.Url(file.name.value)),
          bytes,
          CalibreCollection.extension(file.name)
        )
      )
      .left
      .map(e => SourceCollection.OriginalResultPart1(e.value))

  override def cover(item: CollectionItem): Either[SourceCollection.CoverResultPart1, Option[FetchedFile]] =
    item.cover.map(_.value.trim).filter(p => p.nonEmpty && files.isFile(Path.of(p)).value) match
      case None       => Right(None)
      case Some(path) =>
        val name = CollectionFile.Name(path)
        CalibreCollection
          .read(name)
          .map(b =>
            Some(
              FetchedFile(
                FetchedFile.Source("calibre"),
                Some(FetchedFile.Url(path)),
                b,
                CalibreCollection.extension(name)
              )
            )
          )
          .left
          .map(e => SourceCollection.CoverResultPart1(e.value))

  override def add(
      metadata: CollectionMetadata,
      file: FetchedFile
  ): Either[SourceCollection.AddResultPart1, CollectionId] =
    CalibreCollection
      .staged(LocalFiles.StagedName(metadata.key.value), file)(p => calibre.add(p, CalibreCollection.push(metadata)))
      .map(id => CollectionId(id.value.toString))
      .left
      .map(e => SourceCollection.AddResultPart1(e.value))

  override def replace(
      id: CollectionId,
      key: SourceCollection.ReplaceKey,
      file: FetchedFile
  ): Either[SourceCollection.ReplaceResultPart1, SourceCollection.ReplaceResultPart2] =
    CalibreCollection
      .number(id)
      .flatMap(n => CalibreCollection.staged(LocalFiles.StagedName(key.value), file)(p => calibre.replaceFormat(n, p)))
      .map(f => SourceCollection.ReplaceResultPart2(f.value))
      .left
      .map(e => SourceCollection.ReplaceResultPart1(e.value))

  override def update(
      id: CollectionId,
      metadata: CollectionMetadata
  ): Either[SourceCollection.UpdateResultPart1, List[CollectionChange]] =
    CalibreCollection
      .number(id)
      .flatMap(calibre.update(_, CalibreCollection.push(metadata)))
      .map(_.map(CalibreCollection.change))
      .left
      .map(e => SourceCollection.UpdateResultPart1(e.value))

  override def updateAll(
      links: List[CollectionLink],
      apply: SourceCollection.UpdateAllApply
  ): Either[SourceCollection.UpdateAllResultPart1, List[CollectionUpdateEntry]] =
    val linked = links.flatMap(l =>
      l.id.value.trim.toIntOption.map(n =>
        LinkedRecord(
          LinkedRecord.Citekey(l.metadata.key.value),
          LinkedRecord.CalibreId(n),
          CalibreCollection.push(l.metadata)
        )
      )
    )
    calibre
      .updateAll(linked, Calibre.UpdateAllApply(apply.value))
      .map(_.map(CalibreCollection.entry))
      .left
      .map(e => SourceCollection.UpdateAllResultPart1(e.value))

  override def set(
      id: CollectionId,
      fields: List[(SourceCollection.SetFieldsPart1, SourceCollection.SetFieldsPart2)]
  ): Either[SourceCollection.SetResultPart1, Unit] =
    CalibreCollection
      .number(id)
      .flatMap(
        calibre.set(_, fields.map((name, value) => (Calibre.Field.Name(name.value), Calibre.Field.Value(value.value))))
      )
      .left
      .map(e => SourceCollection.SetResultPart1(e.value))

  override def audit(
      records: List[CollectionRecord]
  ): Either[SourceCollection.AuditResultPart1, List[CollectionFinding]] =
    val recs = records.flatMap(r =>
      r.id.value.trim.toIntOption.map(n =>
        AuditRecord(
          AuditRecord.Citekey(r.key.value),
          AuditRecord.CalibreId(n),
          AuditRecord.Title(r.title.value),
          r.authors.map(a => AuditRecord.Authors(a.value)),
          r.year.map(y => AuditRecord.Year(y.value))
        )
      )
    )
    calibre
      .all()
      .map { books =>
        Calibre.Audit.audit(recs, books.map(b => b.id -> b).toMap).map(CalibreCollection.finding)
      }
      .left
      .map(e => SourceCollection.AuditResultPart1(e.value))

object CalibreCollection:
  type WriteBack = WriteBack.T
  object WriteBack extends Newtype[Boolean, Pure]

  /** The most books a search answers when the host does not say. */
  val SearchLimit: Calibre.SearchLimit = Calibre.SearchLimit(20)

  /** A book as a collection item. */
  def item(b: Calibre.Book): CollectionItem =
    CollectionItem(
      id = CollectionId(b.id.value.toString),
      title = Some(CollectionItem.Title(b.title.value)),
      authors = b.authors.map(a => CollectionItem.Authors(a.value)),
      year = b.year.map(y => Year(y.value)),
      published = b.pubdate.map(p => CollectionItem.Published(p.value)),
      publisher = b.publisher.map(p => CollectionItem.Publisher(p.value)),
      description = b.comments.map(c => CollectionItem.Description(c.value)),
      tags = b.tags.map(t => CollectionItem.Tags(t.value)),
      identifiers =
        b.identifiers.map((k, v) => CollectionItem.IdentifiersKey(k.value) -> CollectionItem.IdentifiersValue(v.value)),
      files = b.formats.map(f => CollectionFile(CollectionFile.Name(f.value))),
      cover = b.cover.map(c => CollectionItem.Cover(c.value))
    )

  /** An item back as the book it was read from. */
  def book(i: CollectionItem): Calibre.Book =
    Calibre.Book(
      id = Calibre.Book.Id(i.id.value.trim.toIntOption.getOrElse(0)),
      title = Calibre.Book.Title(i.title.fold("")(_.value)),
      authors = i.authors.map(a => Calibre.Book.Authors(a.value)),
      pubdate = i.published.map(p => Calibre.Book.Pubdate(p.value)),
      publisher = i.publisher.map(p => Calibre.Book.Publisher(p.value)),
      tags = i.tags.map(t => Calibre.Book.Tags(t.value)),
      comments = i.description.map(c => Calibre.Book.Comments(c.value)),
      identifiers =
        i.identifiers.map((k, v) => Calibre.Book.IdentifiersKey(k.value) -> Calibre.Book.IdentifiersValue(v.value)),
      formats = i.files.map(f => Calibre.Book.Formats(f.name.value)),
      cover = i.cover.map(c => Calibre.Book.Cover(c.value))
    )

  def push(m: CollectionMetadata): Calibre.Push =
    Calibre.Push(
      Calibre.Push.Title(m.title.value),
      m.authors.map(a => Calibre.Push.Authors(a.value)),
      m.doi.map(d => Calibre.Push.Doi(d.value)),
      m.isbn.map(i => Calibre.Push.Isbn(i.value)),
      m.year.map(y => Calibre.Push.Year(y.value)),
      m.publisher.map(p => Calibre.Push.Publisher(p.value)),
      m.summary.map(s => Calibre.Push.Summary(s.value)),
      m.tags.map(t => Calibre.Push.Tags(t.value))
    )

  def change(c: Calibre.Change): CollectionChange =
    CollectionChange(
      CollectionChange.Field(c.field.name.value),
      CollectionChange.From(c.from.value),
      CollectionChange.To(c.to.value)
    )

  def entry(e: UpdateEntry): CollectionUpdateEntry =
    def all(cs: List[Calibre.Change]) = cs.map(change)
    val outcome = e.outcome match
      case UpdateOutcome.Change(cs)         => CollectionUpdateOutcome.Change(all(cs))
      case UpdateOutcome.Applied(cs)        => CollectionUpdateOutcome.Applied(all(cs))
      case UpdateOutcome.Failed(cs, reason) =>
        CollectionUpdateOutcome.Failed(all(cs), CollectionUpdateOutcome.Failed.Reason(reason.value))
      case UpdateOutcome.UpToDate        => CollectionUpdateOutcome.UpToDate
      case UpdateOutcome.Shared          => CollectionUpdateOutcome.Shared
      case UpdateOutcome.Missing         => CollectionUpdateOutcome.Missing
      case UpdateOutcome.Refused(reason) =>
        CollectionUpdateOutcome.Refused(CollectionUpdateOutcome.Refused.Reason(reason.value))
    CollectionUpdateEntry(
      CollectionId(e.calibreId.value.toString),
      e.citekeys.map(k => CollectionUpdateEntry.Keys(k.value)),
      outcome
    )

  def finding(f: AuditFinding): CollectionFinding =
    CollectionFinding(
      CollectionId(f.calibreId.value.toString),
      f.citekeys.map(k => CollectionFinding.Keys(k.value)),
      f.issues.map(i => CollectionIssue(CollectionIssue.Code(i.toString), CollectionIssue.Label(i.label.value))),
      f.calibre.map(item),
      CollectionRecord(
        CollectionRecord.Key(f.record.citekey.value),
        CollectionId(f.record.calibreId.value.toString),
        CollectionRecord.Title(f.record.title.value),
        f.record.authors.map(a => CollectionRecord.Authors(a.value)),
        f.record.year.map(y => Year(y.value))
      ),
      CollectionFinding.Fixable(f.fixable)
    )

  def number(id: CollectionId): Either[Calibre.Problem, Calibre.Book.Id] =
    id.value.trim.toIntOption.map(Calibre.Book.Id(_)).toRight(Calibre.Problem(s"'${id.value}' is not a Calibre id"))

  def extension(name: CollectionFile.Name): FetchedFile.Extension =
    FetchedFile.Extension(name.value.reverse.takeWhile(_ != '.').reverse.toLowerCase(java.util.Locale.ROOT))

  def read(name: CollectionFile.Name)(using files: LocalFiles): Either[Calibre.Problem, FetchedFile.Bytes] =
    Try(FetchedFile.Bytes(files.read(Path.of(name.value)).value)).toEither.left.map(e =>
      Calibre.Problem(s"cannot read ${name.value} (${e.getMessage})")
    )

  /** `file` written as `<a temporary directory>/<name>.<extension>` (the extension is what tells Calibre the format),
    * `f` run on it, and the directory deleted whatever happens.
    */
  def staged[A](name: LocalFiles.StagedName, file: FetchedFile)(
      f: Path => Either[Calibre.Problem, A]
  )(using files: LocalFiles): Either[Calibre.Problem, A] =
    files
      .staged(
        LocalFiles.StagedName(s"${name.value}.${file.extension.value}"),
        LocalFiles.StagedBytes(file.bytes.value)
      )(p => f(p).left.map(e => LocalFiles.StagedUseResultPart1(e.value)))
      .left
      .map(e => Calibre.Problem(e.value))

  /** Whether the server can read the library at `dir`, and when it cannot, what it needs. */
  def reach(dir: Path)(using files: LocalFiles): Either[Calibre.Problem, Unit] =
    val db = dir.resolve("metadata.db")
    if !files.isDirectory(dir).value && !files.exists(db).value then
      Left(
        Calibre.Problem(
          s"there is no Calibre library at $dir — set histmersion.plugins.calibre.library-path to the folder that " +
            "holds metadata.db (a server in a container needs that folder mounted)"
        )
      )
    else
      (Try { files.readPrefix(db, LocalFiles.ReadPrefixLength(16)); () }.toEither.left
        .map {
          case _: java.nio.file.NoSuchFileException =>
            s"there is no metadata.db in $dir — set histmersion.plugins.calibre.library-path to the Calibre library's folder"
          case e =>
            s"the server cannot read $db (${e.getClass.getSimpleName}: ${e.getMessage}) — on macOS a library in " +
              "Documents, Desktop, Downloads, iCloud Drive or on an external volume needs Full Disk Access (or Files " +
              "and Folders) for the server's binary, granted in System Settings → Privacy & Security; a server in a " +
              "container needs the library mounted"
        }
        .map(_ => ()))
        .left
        .map(Calibre.Problem(_))
