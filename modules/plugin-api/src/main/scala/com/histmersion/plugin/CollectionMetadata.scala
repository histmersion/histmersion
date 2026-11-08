package com.histmersion
package plugin

import io.github.iltotore.iron.constraint.any.Pure

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
    key: CollectionMetadata.Key,
    title: CollectionMetadata.Title,
    authors: List[CollectionMetadata.Authors],
    doi: Option[CollectionMetadata.Doi] = None,
    isbn: Option[CollectionMetadata.Isbn] = None,
    year: Option[Year] = None,
    publisher: Option[CollectionMetadata.Publisher] = None,
    summary: Option[CollectionMetadata.Summary] = None,
    tags: List[CollectionMetadata.Tags] = Nil
)

object CollectionMetadata:
  type Key = Key.T
  object Key extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Authors = Authors.T
  object Authors extends Newtype[String, Pure]
  type Doi = Doi.T
  object Doi extends Newtype[String, Pure]
  type Isbn = Isbn.T
  object Isbn extends Newtype[String, Pure]
  type Publisher = Publisher.T
  object Publisher extends Newtype[String, Pure]
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
