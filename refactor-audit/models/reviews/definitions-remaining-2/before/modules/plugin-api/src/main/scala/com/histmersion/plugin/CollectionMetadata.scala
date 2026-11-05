package com.histmersion.plugin

/** What the host sends into a collection for one of its sources: a new item's metadata ([[SourceCollection.add]]), or
  * what an existing item should say ([[SourceCollection.update]]).
  *
  * @param key
  *   the source's key in the host (its citekey); a collection may name a file after it
  * @param title
  *   the source's title
  * @param authors
  *   its authors, one name each ("Stocker, Sharon R." is one author)
  * @param doi
  *   its DOI
  * @param isbn
  *   its ISBN
  * @param year
  *   the year the host cites it by
  * @param publisher
  *   its venue or publisher
  * @param summary
  *   a short summary
  * @param tags
  *   its topics
  */
final case class CollectionMetadata(
    key: String,
    title: String,
    authors: List[String],
    doi: Option[String] = None,
    isbn: Option[String] = None,
    year: Option[Year] = None,
    publisher: Option[String] = None,
    summary: Option[String] = None,
    tags: List[String] = Nil
)
