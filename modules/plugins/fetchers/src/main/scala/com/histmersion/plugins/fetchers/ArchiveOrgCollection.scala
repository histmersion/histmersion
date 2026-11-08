package com.histmersion
package plugins.fetchers

import com.histmersion.plugin.*
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.archiveorg.{ArchiveFile, ArchiveItem, ArchiveOrg}
import com.histmersion.upstreams.PageSize
import com.histmersion.upstreams.ArchiveId

/** archive.org's texts as a read-only source collection: its search, an item's metadata and files, and a public item's
  * file. A lending-only item is described (`restricted`), never downloaded: it is borrowed on archive.org.
  */
final class ArchiveOrgCollection(client: () => ArchiveOrg) extends SourceCollection:
  val title: SourceCollection.TitleResult = SourceCollection.TitleResult("archive.org")
  override val description: SourceCollection.DescriptionResult =
    SourceCollection.DescriptionResult("archive.org's public texts (lending-only items are refused)")

  def search(query: CollectionQuery): Either[SourceCollection.SearchResultPart1, List[CollectionItem]] =
    client()
      .search(
        ArchiveOrg.SearchQuery(query.text.fold("")(_.value)),
        PageSize(query.limit.getOrElse(ArchiveOrgCollection.Rows).value)
      )
      .left
      .map(e => SourceCollection.SearchResultPart1(e.value))
      .map(_.map(ArchiveOrgCollection.item))

  def item(id: CollectionId): Either[SourceCollection.ItemResultPart1, Option[CollectionItem]] =
    client()
      .info(ArchiveId(id.value))
      .left
      .map(e => SourceCollection.ItemResultPart1(e.value))
      .map(i => Some(ArchiveOrgCollection.item(i)))

  def file(item: CollectionItem, format: Option[SourceCollection.FileFormat]): Either[SourceCollection.FileResultPart1, CollectionFile] =
    AcquireParse
      .pickArchiveFile(item.files.map(ArchiveOrgCollection.archiveFile), format.map(f => AcquireParse.WantedFileKind(f.value)))
      .map(ArchiveOrgCollection.collectionFile)
      .toRight(SourceCollection.FileResultPart1(s"'${item.id.value}' has no epub/pdf file to download"))

  def original(item: CollectionItem, file: CollectionFile): Either[SourceCollection.OriginalResultPart1, FetchedFile] =
    if item.restricted.value then
      Left(SourceCollection.OriginalResultPart1(s"'${item.id.value}' is a lending-only item — borrow it on archive.org"))
    else
      client()
        .download(ArchiveId(item.id.value), ArchiveOrg.DownloadFile(file.name.value))
        .left
        .map(e => SourceCollection.OriginalResultPart1(e.value))

object ArchiveOrgCollection:
  /** The hits a search answers when the host does not say. */
  val Rows: CollectionQuery.Limit = CollectionQuery.Limit(8)

  private def archiveFile(f: CollectionFile): ArchiveFile =
    ArchiveFile(
      ArchiveFile.Name(f.name.value),
      f.format.map(x => ArchiveFile.Format(x.value)),
      ArchiveFile.Megabytes(f.megabytes.fold(0.0)(_.value))
    )

  private def collectionFile(f: ArchiveFile): CollectionFile =
    CollectionFile(
      CollectionFile.Name(f.name.value),
      f.format.map(x => CollectionFile.Format(x.value)),
      Some(CollectionFile.Megabytes(f.megabytes.value))
    )

  def item(i: ArchiveItem): CollectionItem =
    CollectionItem(
      id = CollectionId(i.identifier.value),
      title = i.title.map(t => CollectionItem.Title(t.value)),
      authors = i.creator.map(c => CollectionItem.Authors(c.value)),
      year = i.year,
      publisher = i.publisher.map(p => CollectionItem.Publisher(p.value)),
      description = i.description.map(d => CollectionItem.Description(d.value)),
      files = i.files.map(collectionFile),
      restricted = CollectionItem.Restricted(i.lendingOnly.value)
    )
