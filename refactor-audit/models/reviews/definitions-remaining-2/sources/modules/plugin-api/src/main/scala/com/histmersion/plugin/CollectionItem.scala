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
    title: Option[CollectionItem.Title] = None,
    authors: List[CollectionItem.Authors] = Nil,
    year: Option[Year] = None,
    published: Option[CollectionItem.Published] = None,
    publisher: Option[CollectionItem.Publisher] = None,
    description: Option[CollectionItem.Description] = None,
    tags: List[CollectionItem.Tags] = Nil,
    identifiers: Map[CollectionItem.IdentifiersKey, CollectionItem.IdentifiersValue] = Map.empty,
    files: List[CollectionFile] = Nil,
    cover: Option[CollectionItem.Cover] = None,
    restricted: CollectionItem.Restricted = false
)

object CollectionItem:
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Published = Published.T
  object Published extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Publisher = Publisher.T
  object Publisher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Description = Description.T
  object Description extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentifiersKey = IdentifiersKey.T
  object IdentifiersKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentifiersValue = IdentifiersValue.T
  object IdentifiersValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Cover = Cover.T
  object Cover extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Restricted = Restricted.T
  object Restricted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
