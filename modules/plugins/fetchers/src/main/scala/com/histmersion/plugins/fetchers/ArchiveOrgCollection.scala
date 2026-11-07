package com.histmersion.plugins.fetchers

import com.histmersion.plugin.*
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.archiveorg.{ArchiveFile, ArchiveItem, ArchiveOrg}

/** archive.org's texts as a read-only source collection: its search, an item's metadata and files, and a public item's
  * file. A lending-only item is described (`restricted`), never downloaded: it is borrowed on archive.org.
  */
final class ArchiveOrgCollection(client: () => ArchiveOrg) extends SourceCollection:
  val title: com.histmersion.plugin.SourceCollection.TitleResult = com.histmersion.plugin.SourceCollection.TitleResult("archive.org")
  override val description: com.histmersion.plugin.SourceCollection.DescriptionResult =
    com.histmersion.plugin.SourceCollection.DescriptionResult("archive.org's public texts (lending-only items are refused)")

  def search(query: CollectionQuery): Either[com.histmersion.plugin.SourceCollection.SearchResultPart1, List[CollectionItem]] =
    client()
      .search(
        ArchiveOrg.SearchQuery(query.text.fold("")(_.value)),
        com.histmersion.upstreams.PageSize(query.limit.getOrElse(ArchiveOrgCollection.Rows).value)
      )
      .left
      .map(e => com.histmersion.plugin.SourceCollection.SearchResultPart1(e.value))
      .map(_.map(ArchiveOrgCollection.item))

  def item(id: CollectionId): Either[com.histmersion.plugin.SourceCollection.ItemResultPart1, Option[CollectionItem]] =
    client()
      .info(com.histmersion.upstreams.ArchiveId(id.value))
      .left
      .map(e => com.histmersion.plugin.SourceCollection.ItemResultPart1(e.value))
      .map(i => Some(ArchiveOrgCollection.item(i)))

  def file(item: CollectionItem, format: Option[com.histmersion.plugin.SourceCollection.FileFormat]): Either[com.histmersion.plugin.SourceCollection.FileResultPart1, CollectionFile] =
    AcquireParse
      .pickArchiveFile(item.files.map(ArchiveOrgCollection.archiveFile), format.map(f => AcquireParse.WantedFileKind(f.value)))
      .map(ArchiveOrgCollection.collectionFile)
      .toRight(com.histmersion.plugin.SourceCollection.FileResultPart1(s"'${item.id.value}' has no epub/pdf file to download"))

  def original(item: CollectionItem, file: CollectionFile): Either[com.histmersion.plugin.SourceCollection.OriginalResultPart1, FetchedFile] =
    if item.restricted.value then
      Left(com.histmersion.plugin.SourceCollection.OriginalResultPart1(s"'${item.id.value}' is a lending-only item — borrow it on archive.org"))
    else
      client()
        .download(com.histmersion.upstreams.ArchiveId(item.id.value), ArchiveOrg.DownloadFile(file.name.value))
        .left
        .map(e => com.histmersion.plugin.SourceCollection.OriginalResultPart1(e.value))

object ArchiveOrgCollection:
  /** The hits a search answers when the host does not say. */
  val Rows: com.histmersion.plugin.CollectionQuery.Limit = com.histmersion.plugin.CollectionQuery.Limit(8)

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
