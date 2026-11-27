package com.histmersion
package plugins.calibre
import com.histmersion.util.problem.{External, Problem, Problems}

import com.histmersion.plugin.*

import java.nio.file.Path
import scala.util.Try
import io.github.iltotore.iron.constraint.any.Pure
import io.github.iltotore.iron.constraint.string.Match
import com.histmersion.lint.{tolerated, LintRule}

/** The Calibre library as a [[SourceCollection]]: [[Calibre]] read and written, its books as collection items. Writes
  * only with `writeBack`, and then refused while the Calibre GUI holds the library.
  */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final class CalibreCollection(library: Calibre.Library, writeBack: CalibreCollection.WriteBack, shell: Shell)(using
    files: LocalFiles
) extends SourceCollection:
  private val calibre = Calibre(library, shell)

  val title: SourceCollection.Title = SourceCollection.Title("Calibre")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override val description: SourceCollection.Description =
    SourceCollection.Description(
      s"The Calibre library at ${library.library.value}: read with calibredb (sqlite3 while the GUI holds it)" +
        (if (writeBack).value then ", written back with calibredb" else ", read-only")
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def capabilities: Set[CollectionCapability] =
    if (writeBack).value then Set(CollectionCapability.Read, CollectionCapability.Add, CollectionCapability.Update)
    else Set(CollectionCapability.Read)

  override def readOnlyReason: Problem =
    Problem.Refused.Disabled(
      Problem.Refused.Feature("write-back"),
      Problem.SettingKey("histmersion.plugins.calibre.write-back"),
      Some(Problem.SettingKey("HISTMERSION_PLUGIN_CALIBRE_WRITE_BACK")),
      Some(Problem.Refused.Effect.WritesLibrary)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def reach: Either[Problems, Unit] =
    CalibreCollection.reach(Path.of(library.library.value))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def search(query: CollectionQuery): Either[Problems, List[CollectionItem]] =
    (query.text match
      case Some(text) =>
        calibre.search(text, query.limit.fold(CalibreCollection.SearchLimit)(l => Calibre.SearchLimit(l.value)))
      case None => query.limit.fold(calibre.all())(l => calibre.all(Calibre.AllLimit(l.value)))
    ).map(_.map(CalibreCollection.item))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def item(id: CollectionId): Either[Problems, Option[CollectionItem]] =
    id.value.trim.toIntOption
      .fold[Either[Problems, Option[CollectionItem]]](Right(None))(n =>
        calibre.byId(Calibre.Book.Id(n)).map(_.map(CalibreCollection.item))
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def admits(item: CollectionItem): Either[Problems, Unit] =
    Calibre
      .guard(Calibre.Push.Title(item.title.fold("")(_.value)), item.authors.map(a => Calibre.Push.Authors(a.value)))
      .left
      .map(_.at(Problem.Field(s"Calibre id ${item.id}")))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def file(
      item: CollectionItem,
      format: Option[SourceCollection.FileFormat]
  ): Either[Problems, CollectionFile] =
    CalibreCollection
      .book(item)
      .pick(format.map(f => CollectionFile.Format(f.value)))
      .map(f => CollectionFile(CollectionFile.Name(f.value)))
      .toRight(
        Calibre.said(
          s"Calibre id ${item.id.value} has no pdf/epub (formats: ${item.files.map(_.name.value).mkString(", ")}); " +
            "KFX/MOBI must be converted in Calibre first"
        )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def original(item: CollectionItem, file: CollectionFile): Either[Problems, FetchedFile] =
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def cover(item: CollectionItem): Either[Problems, Option[FetchedFile]] =
    item.cover.map(_.value.trim).filter(p => p.nonEmpty && files.isFile(Path.of(p))) match
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def add(
      metadata: CollectionMetadata,
      file: FetchedFile
  ): Either[Problems, CollectionId] =
    CalibreCollection
      .staged(LocalFiles.StagedName(metadata.key.value), file)(p => calibre.add(p, CalibreCollection.push(metadata)))
      .map(id => CollectionId(id.value.toString))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def replace(
      id: CollectionId,
      key: SourceCollection.ReplaceKey,
      file: FetchedFile
  ): Either[Problems, SourceCollection.FileFormat] =
    CalibreCollection
      .number(id)
      .flatMap(n => CalibreCollection.staged(LocalFiles.StagedName(key.value), file)(p => calibre.replaceFormat(n, p)))
      .map(f => SourceCollection.FileFormat(f.value))

  override def update(
      id: CollectionId,
      metadata: CollectionMetadata
  ): Either[Problems, List[CollectionChange]] =
    CalibreCollection
      .number(id)
      .flatMap(calibre.update(_, CalibreCollection.push(metadata)))
      .map(_.map(CalibreCollection.change))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def updateAll(
      links: List[CollectionLink],
      apply: SourceCollection.UpdateAllApply
  ): Either[Problems, List[CollectionUpdateEntry]] =
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def set(
      id: CollectionId,
      fields: List[(SourceCollection.FieldName, SourceCollection.FieldValue)]
  ): Either[Problems, Unit] =
    CalibreCollection
      .number(id)
      .flatMap(
        calibre.set(_, fields.map((name, value) => (Calibre.Field.Name(name.value), Calibre.Field.Value(value.value))))
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def audit(
      records: List[CollectionRecord]
  ): Either[Problems, List[CollectionFinding]] =
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

object CalibreCollection:

  /** A Calibre entry's id as a collection id spells it: decimal digits that fit an `Int`. */
  type IdText = IdText.T
  object IdText extends Newtype[String, Match["[0-9]{1,9}"]]
  export Calibre.Settings.WriteBack

  /** The most books a search answers when the host does not say. */
  val SearchLimit: Calibre.SearchLimit = Calibre.SearchLimit(20)

  /** A book as a collection item. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def change(c: Calibre.Change): CollectionChange =
    CollectionChange(
      CollectionChange.Field(c.field.name.value),
      CollectionChange.From(c.from.value),
      CollectionChange.To(c.to.value)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def entry(e: UpdateEntry): CollectionUpdateEntry =
    def all(cs: List[Calibre.Change]) = cs.map(change)
    val outcome = e.outcome match
      case UpdateOutcome.Change(cs)         => CollectionUpdateOutcome.Change(all(cs))
      case UpdateOutcome.Applied(cs)        => CollectionUpdateOutcome.Applied(all(cs))
      case UpdateOutcome.Failed(cs, reason) =>
        CollectionUpdateOutcome.Failed(all(cs), reason)
      case UpdateOutcome.UpToDate        => CollectionUpdateOutcome.UpToDate
      case UpdateOutcome.Shared          => CollectionUpdateOutcome.Shared
      case UpdateOutcome.Missing         => CollectionUpdateOutcome.Missing
      case UpdateOutcome.Refused(reason) =>
        CollectionUpdateOutcome.Refused(reason)
    CollectionUpdateEntry(
      CollectionId(e.calibreId.value.toString),
      e.citekeys.map(k => CollectionUpdateEntry.Keys(k.value)),
      outcome
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def number(id: CollectionId): Either[Problems, Calibre.Book.Id] =
    IdText
      .checked(id.value.trim)
      .left
      .map(Problem.Invalid.malformed)
      .map(t => Calibre.Book.Id(t.value.toInt))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def extension(name: CollectionFile.Name): FetchedFile.Extension =
    FetchedFile.Extension(name.value.reverse.takeWhile(_ != '.').reverse.toLowerCase(java.util.Locale.ROOT))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def read(name: CollectionFile.Name)(using files: LocalFiles): Either[Problems, FetchedFile.Bytes] =
    Try(FetchedFile.Bytes(files.read(Path.of(name.value)).value)).toEither.left.map(e =>
      Problem.Io.File(Problem.Io.Access.Read, Problem.Io.Path(s"$name"), External.Reported.of(e))
    )

  /** `file` written as `<a temporary directory>/<name>.<extension>` (the extension is what tells Calibre the format),
    * `f` run on it, and the directory deleted whatever happens.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def staged[A](name: LocalFiles.StagedName, file: FetchedFile)(
      f: Path => Either[Problems, A]
  )(using files: LocalFiles): Either[Problems, A] =
    files
      .staged(
        LocalFiles.StagedName(s"${name.value}.${file.extension.value}"),
        LocalFiles.Content(file.bytes.value)
      )(p => f(p))

  /** Whether the server can read the library at `dir`, and when it cannot, what it needs. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def reach(dir: Path)(using files: LocalFiles): Either[Problems, Unit] =
    val db = dir.resolve("metadata.db")
    if !files.isDirectory(dir) && !files.exists(db) then
      Left(
        Problems.of(
          Problem.Io.Wrong(Problem.Io.Path(s"$dir"), Problem.Io.Kind.Directory),
          Calibre.said(
            "set histmersion.plugins.calibre.library-path to the folder that holds metadata.db (a server in a " +
              "container needs that folder mounted)"
          )
        )
      )
    else
      (Try { files.readPrefix(db, LocalFiles.ReadPrefixLength(16)); () }.toEither.left
        .map {
          case _: java.nio.file.NoSuchFileException =>
            Problems.of(
              Problem.Io.Wrong(Problem.Io.Path(s"$db"), Problem.Io.Kind.Present),
              Calibre.said("set histmersion.plugins.calibre.library-path to the Calibre library's folder")
            )
          case e =>
            Problems.of(
              Problem.Io.File(Problem.Io.Access.Read, Problem.Io.Path(s"$db"), External.Reported.of(e)),
              Calibre.said(
                "on macOS a library in Documents, Desktop, Downloads, iCloud Drive or on an external volume needs Full " +
                  "Disk Access (or Files and Folders) for the server's binary, granted in System Settings → Privacy & " +
                  "Security; a server in a container needs the library mounted"
              )
            )
        }
        .map(_ => ())
    )
