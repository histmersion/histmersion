package com.histmersion.plugin

/** One item of a [[SourceCollection]] — a book in a Calibre library, an item of archive.org — as the collection
  * describes it. Plain data: the host decides what a source made from it holds.
  *
  * @param id
  *   its id in the collection
  * @param title
  *   its title, when it has one
  * @param authors
  *   its authors (or creators), in the collection's order and spelling
  * @param year
  *   its year of publication, when the collection knows it (a placeholder date is no year)
  * @param published
  *   the publication date exactly as the collection stores it
  * @param publisher
  *   its publisher
  * @param description
  *   the collection's description of it (Calibre's comments, archive.org's description), as stored — it may be HTML
  * @param tags
  *   the collection's tags
  * @param identifiers
  *   its identifiers by scheme (`doi`, `isbn`, `google`, …)
  * @param files
  *   its files
  * @param cover
  *   where the collection keeps its cover, when it has one
  * @param restricted
  *   the collection lends the item only (an archive.org lending-only scan): the host never downloads it
  */
final case class CollectionItem(
    id: CollectionId,
    title: Option[String] = None,
    authors: List[String] = Nil,
    year: Option[Year] = None,
    published: Option[String] = None,
    publisher: Option[String] = None,
    description: Option[String] = None,
    tags: List[String] = Nil,
    identifiers: Map[String, String] = Map.empty,
    files: List[CollectionFile] = Nil,
    cover: Option[String] = None,
    restricted: Boolean = false
)
