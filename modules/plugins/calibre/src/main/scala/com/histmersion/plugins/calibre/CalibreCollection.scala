package com.histmersion.plugins.calibre

import com.histmersion.plugin.*

import java.nio.file.{Files, Path}
import scala.util.Try

/** The Calibre library as a [[SourceCollection]]: [[Calibre]] read and written, its books as collection items. Writes
  * only with `writeBack`, and then refused while the Calibre GUI holds the library.
  */
final class CalibreCollection(library: CalibreLibrary, writeBack: Boolean, shell: Shell) extends SourceCollection:
  private val calibre = Calibre(library, shell)

  val title: String = "Calibre"
  override val description: String =
    s"The Calibre library at ${library.library}: read with calibredb (sqlite3 while the GUI holds it)" +
      (if writeBack then ", written back with calibredb" else ", read-only")

  override def capabilities: Set[CollectionCapability] =
    if writeBack then Set(CollectionCapability.Read, CollectionCapability.Add, CollectionCapability.Update)
    else Set(CollectionCapability.Read)

  override def readOnlyReason: String =
    "write-back is off — set histmersion.plugins.calibre.write-back = true " +
      "(HISTMERSION_PLUGIN_CALIBRE_WRITE_BACK=true) to let the server write to the library"

  override def reach: Either[String, Unit] = CalibreCollection.reach(Path.of(library.library))

  def search(query: CollectionQuery): Either[String, List[CollectionItem]] =
    (query.text match
      case Some(text) => calibre.search(text, query.limit.getOrElse(CalibreCollection.SearchLimit))
      case None       => query.limit.fold(calibre.all())(calibre.all(_))
    ).map(_.map(CalibreCollection.item))

  def item(id: CollectionId): Either[String, Option[CollectionItem]] =
    id.value.trim.toIntOption.fold(Right(None))(calibre.byId(_).map(_.map(CalibreCollection.item)))

  override def admits(item: CollectionItem): Either[String, Unit] =
    Calibre.guard(item.title.getOrElse(""), item.authors).left.map(e => s"Calibre id ${item.id.value} has $e")

  def file(item: CollectionItem, format: Option[String]): Either[String, CollectionFile] =
    CalibreCollection
      .book(item)
      .pick(format)
      .map(CollectionFile(_))
      .toRight(
        s"Calibre id ${item.id.value} has no pdf/epub (formats: ${item.files.map(_.name).mkString(", ")}); " +
          "KFX/MOBI must be converted in Calibre first"
      )

  def original(item: CollectionItem, file: CollectionFile): Either[String, FetchedFile] =
    CalibreCollection
      .read(file.name)
      .map(bytes => FetchedFile("calibre", Some(file.name), bytes, CalibreCollection.extension(file.name)))

  override def cover(item: CollectionItem): Either[String, Option[FetchedFile]] =
    item.cover.map(_.trim).filter(p => p.nonEmpty && Files.isRegularFile(Path.of(p))) match
      case None       => Right(None)
      case Some(path) =>
        CalibreCollection.read(path).map(b => Some(FetchedFile("calibre", Some(path), b, CalibreCollection.extension(path))))

  override def add(metadata: CollectionMetadata, file: FetchedFile): Either[String, CollectionId] =
    CalibreCollection.staged(metadata.key, file)(p => calibre.add(p, CalibreCollection.push(metadata)))
      .map(id => CollectionId(id.toString))

  override def replace(id: CollectionId, key: String, file: FetchedFile): Either[String, String] =
    CalibreCollection.number(id).flatMap(n => CalibreCollection.staged(key, file)(p => calibre.replaceFormat(n, p)))

  override def update(id: CollectionId, metadata: CollectionMetadata): Either[String, List[CollectionChange]] =
    CalibreCollection
      .number(id)
      .flatMap(calibre.update(_, CalibreCollection.push(metadata)))
      .map(_.map(CalibreCollection.change))

  override def updateAll(links: List[CollectionLink], apply: Boolean): Either[String, List[CollectionUpdateEntry]] =
    val linked = links.flatMap(l =>
      l.id.value.trim.toIntOption.map(LinkedRecord(l.metadata.key, _, CalibreCollection.push(l.metadata)))
    )
    calibre.updateAll(linked, apply).map(_.map(CalibreCollection.entry))

  override def set(id: CollectionId, fields: List[(String, String)]): Either[String, Unit] =
    CalibreCollection.number(id).flatMap(calibre.set(_, fields))

  override def audit(records: List[CollectionRecord]): Either[String, List[CollectionFinding]] =
    val recs = records.flatMap(r =>
      r.id.value.trim.toIntOption.map(AuditRecord(r.key, _, r.title, r.authors, r.year.map(_.value)))
    )
    calibre.all().map { books =>
      CalibreAudit.audit(recs, books.map(b => b.id -> b).toMap).map(CalibreCollection.finding)
    }

object CalibreCollection:
  /** The most books a search answers when the host does not say. */
  val SearchLimit: Int = 20

  /** A book as a collection item. */
  def item(b: CalibreBook): CollectionItem =
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
  def book(i: CollectionItem): CalibreBook =
    CalibreBook(
      id = i.id.value.trim.toIntOption.getOrElse(0),
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

  def push(m: CollectionMetadata): CalibrePush =
    CalibrePush(m.title, m.authors, m.doi, m.isbn, m.year.map(_.value), m.publisher, m.summary, m.tags)

  def change(c: CalibreChange): CollectionChange = CollectionChange(c.field.name, c.from, c.to)

  def entry(e: UpdateEntry): CollectionUpdateEntry =
    def all(cs: List[CalibreChange]) = cs.map(change)
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
      f.issues.map(i => CollectionIssue(i.toString, i.label)),
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

  def number(id: CollectionId): Either[String, Int] =
    id.value.trim.toIntOption.toRight(s"'${id.value}' is not a Calibre id")

  def extension(name: String): String = name.reverse.takeWhile(_ != '.').reverse.toLowerCase(java.util.Locale.ROOT)

  def read(path: String): Either[String, Array[Byte]] =
    Try(Files.readAllBytes(Path.of(path))).toEither.left.map(e => s"cannot read $path (${e.getMessage})")

  /** `file` written as `<a temporary directory>/<name>.<extension>` (the extension is what tells Calibre the format),
    * `f` run on it, and the directory deleted whatever happens.
    */
  def staged[A](name: String, file: FetchedFile)(f: Path => Either[String, A]): Either[String, A] =
    Try(Files.createTempDirectory("histmersion-original-")).toEither.left
      .map(e => s"cannot stage the file: ${e.getMessage}")
      .flatMap { dir =>
        try
          val p = dir.resolve(s"$name.${file.extension}")
          Try(Files.write(p, file.bytes)).toEither.left.map(e => s"cannot stage the file: ${e.getMessage}").flatMap(f)
        finally
          val s = Files.list(dir)
          try s.forEach(p => Files.deleteIfExists(p): Unit)
          finally s.close()
          Files.deleteIfExists(dir): Unit
      }

  /** Whether the server can read the library at `dir`, and when it cannot, what it needs. */
  def reach(dir: Path): Either[String, Unit] =
    val db = dir.resolve("metadata.db")
    if !Files.isDirectory(dir) && !Files.exists(db) then
      Left(
        s"there is no Calibre library at $dir — set histmersion.plugins.calibre.library-path to the folder that " +
          "holds metadata.db (a server in a container needs that folder mounted)"
      )
    else
      Try {
        val in = Files.newInputStream(db)
        try in.read(new Array[Byte](16))
        finally in.close()
      }.toEither.left.map {
        case _: java.nio.file.NoSuchFileException =>
          s"there is no metadata.db in $dir — set histmersion.plugins.calibre.library-path to the Calibre library's folder"
        case e =>
          s"the server cannot read $db (${e.getClass.getSimpleName}: ${e.getMessage}) — on macOS a library in " +
            "Documents, Desktop, Downloads, iCloud Drive or on an external volume needs Full Disk Access (or Files " +
            "and Folders) for the server's binary, granted in System Settings → Privacy & Security; a server in a " +
            "container needs the library mounted"
      }.map(_ => ())
