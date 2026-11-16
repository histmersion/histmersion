package com.histmersion
package plugins.fetchers
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.*
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.archiveorg.{ArchiveFile, ArchiveItem, ArchiveOrg}
import com.histmersion.upstreams.PageSize
import com.histmersion.upstreams.ArchiveId

/** archive.org's texts as a read-only source collection: its search, an item's metadata and files, and a public item's
  * file. A lending-only item is described (`restricted`), never downloaded: it is borrowed on archive.org.
  */
final class ArchiveOrgCollection(client: () => ArchiveOrg) extends SourceCollection:
  val title: SourceCollection.Title = SourceCollection.Title("archive.org")
  override val description: SourceCollection.Description =
    SourceCollection.Description("archive.org's public texts (lending-only items are refused)")

  def search(query: CollectionQuery): Either[Problem, List[CollectionItem]] =
    client()
      .search(
        ArchiveOrg.SearchQuery(query.text.fold("")(_.value)),
        PageSize(query.limit.getOrElse(ArchiveOrgCollection.Rows).value)
      )
      .map(_.map(ArchiveOrgCollection.item))

  def item(id: CollectionId): Either[Problem, Option[CollectionItem]] =
    client()
      .info(ArchiveId(id.value))
      .map(i => Some(ArchiveOrgCollection.item(i)))

  def file(
      item: CollectionItem,
      format: Option[SourceCollection.FileFormat]
  ): Either[Problem, CollectionFile] =
    AcquireParse
      .pickArchiveFile(
        item.files.map(ArchiveOrgCollection.archiveFile),
        format.map(f => AcquireParse.WantedFileKind(f.value))
      )
      .map(ArchiveOrgCollection.collectionFile)
      .toRight(
        Problem.NotFound.Entity(Problem.TypeName("Copy"), Problem.Raw(s"${item.id.value}"))
      )

  def original(item: CollectionItem, file: CollectionFile): Either[Problem, FetchedFile] =
    if item.restricted.value then
      Left(
        Problem.Refused.Policy(Problem.Refused.Rule.LendingOnly)
      )
    else
      client()
        .download(ArchiveId(item.id.value), ArchiveOrg.DownloadFile(file.name.value))

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
