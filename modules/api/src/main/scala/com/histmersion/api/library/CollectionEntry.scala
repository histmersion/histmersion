package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given

/** One item of a collection as a listing shows it (a Calibre book): its id, title, authors, year, publisher, tags,
  * identifiers by scheme and its files.
  */
final case class CollectionEntry(
    id: CollectionEntry.Id,
    title: com.histmersion.api.library.Title,
    authors: List[com.histmersion.api.library.AuthorName],
    year: Option[com.histmersion.api.catalogue.Year],
    publisher: Option[CollectionEntry.Publisher],
    tags: List[CollectionEntry.Tags],
    identifiers: Map[CollectionEntry.IdentifiersKey, CollectionEntry.IdentifiersValue],
    formats: List[CollectionEntry.Formats]
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionEntry:
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Publisher = Publisher.T
  object Publisher extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IdentifiersKey = IdentifiersKey.T
  object IdentifiersKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.io.circe.KeyEncoder[T] = _root_.io.circe.KeyEncoder.encodeKeyString.contramap(_.value)
    given _root_.io.circe.KeyDecoder[T] = _root_.io.circe.KeyDecoder.decodeKeyString.map(IdentifiersKey(_))
    given Schema[Map[T, IdentifiersValue]] = Schema.schemaForMap(_.value)
  type IdentifiersValue = IdentifiersValue.T
  object IdentifiersValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Formats = Formats.T
  object Formats extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
