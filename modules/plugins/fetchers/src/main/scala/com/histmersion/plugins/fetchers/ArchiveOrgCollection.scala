package com.histmersion.plugins.fetchers

import com.histmersion.plugin.*
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.archiveorg.{ArchiveFile, ArchiveItem, ArchiveOrg}

/** archive.org's texts as a read-only source collection: its search, an item's metadata and files, and a public item's
  * file. A lending-only item is described (`restricted`), never downloaded: it is borrowed on archive.org.
  */
final class ArchiveOrgCollection(client: () => ArchiveOrg) extends SourceCollection:
  val title: String = "archive.org"
  override val description: String = "archive.org's public texts (lending-only items are refused)"

  def search(query: CollectionQuery): Either[String, List[CollectionItem]] =
    client()
      .search(query.text.getOrElse(""), query.limit.getOrElse(ArchiveOrgCollection.Rows))
      .map(_.map(ArchiveOrgCollection.item))

  def item(id: CollectionId): Either[String, Option[CollectionItem]] =
    client().info(id.value).map(i => Some(ArchiveOrgCollection.item(i)))

  def file(item: CollectionItem, format: Option[String]): Either[String, CollectionFile] =
    AcquireParse
      .pickArchiveFile(item.files.map(f => ArchiveFile(f.name, f.format, f.megabytes.getOrElse(0.0))), format)
      .map(f => CollectionFile(f.name, f.format, Some(f.megabytes)))
      .toRight(s"'${item.id.value}' has no epub/pdf file to download")

  def original(item: CollectionItem, file: CollectionFile): Either[String, FetchedFile] =
    if item.restricted then Left(s"'${item.id.value}' is a lending-only item — borrow it on archive.org")
    else client().download(item.id.value, file.name)

object ArchiveOrgCollection:
  /** The hits a search answers when the host does not say. */
  val Rows: Int = 8

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
