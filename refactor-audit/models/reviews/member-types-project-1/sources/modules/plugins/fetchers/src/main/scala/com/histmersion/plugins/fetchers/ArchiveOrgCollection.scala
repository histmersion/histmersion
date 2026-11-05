package com.histmersion.plugins.fetchers

import com.histmersion.plugin.*
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.archiveorg.{ArchiveFile, ArchiveItem, ArchiveOrg}

/** archive.org's texts as a read-only source collection: its search, an item's metadata and files, and a public item's
  * file. A lending-only item is described (`restricted`), never downloaded: it is borrowed on archive.org.
  */
final class ArchiveOrgCollection(client: () => ArchiveOrg) extends SourceCollection:
  val title: com.histmersion.plugins.fetchers.ArchiveOrgCollection.Title = "archive.org"
  override val description: com.histmersion.plugins.fetchers.ArchiveOrgCollection.Description = "archive.org's public texts (lending-only items are refused)"

  def search(query: CollectionQuery): Either[com.histmersion.plugins.fetchers.ArchiveOrgCollection.SearchResultPart1, List[CollectionItem]] =
    client()
      .search(query.text.getOrElse(""), query.limit.getOrElse(ArchiveOrgCollection.Rows))
      .map(_.map(ArchiveOrgCollection.item))

  def item(id: CollectionId): Either[com.histmersion.plugins.fetchers.ArchiveOrgCollection.ItemResultPart1, Option[CollectionItem]] =
    client().info(id.value).map(i => Some(ArchiveOrgCollection.item(i)))

  def file(item: CollectionItem, format: Option[com.histmersion.plugins.fetchers.ArchiveOrgCollection.FileFormat]): Either[com.histmersion.plugins.fetchers.ArchiveOrgCollection.FileResultPart1, CollectionFile] =
    AcquireParse
      .pickArchiveFile(item.files.map(f => ArchiveFile(f.name, f.format, f.megabytes.getOrElse(0.0))), format)
      .map(f => CollectionFile(f.name, f.format, Some(f.megabytes)))
      .toRight(s"'${item.id.value}' has no epub/pdf file to download")

  def original(item: CollectionItem, file: CollectionFile): Either[com.histmersion.plugins.fetchers.ArchiveOrgCollection.OriginalResultPart1, FetchedFile] =
    if item.restricted then Left(s"'${item.id.value}' is a lending-only item — borrow it on archive.org")
    else client().download(item.id.value, file.name)

object ArchiveOrgCollection:
  /** The hits a search answers when the host does not say. */
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
  type Rows = Rows.T
  object Rows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val Rows: com.histmersion.plugins.fetchers.ArchiveOrgCollection.Rows = 8

  def item(i: ArchiveItem): CollectionItem =
    CollectionItem(
      id = CollectionId(i.identifier),
      title = i.title,
      authors = i.creator,
      year = i.year.map(Year(_)),
      publisher = i.publisher,
      description = i.description,
      files = i.files.map(f => CollectionFile(f.name, f.format, Some(f.megabytes))),
      restricted = i.lendingOnly
    )
