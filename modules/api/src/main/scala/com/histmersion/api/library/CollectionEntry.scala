package com.histmersion
package api.library
import com.github.plokhotnyuk.jsoniter_scala.core.JsonKeyCodec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

/** One item of a collection as a listing shows it (a Calibre book): its id, title, authors, year, publisher, tags,
  * identifiers by scheme and its files.
  */
final case class CollectionEntry(
    id: CollectionEntry.Id,
    title: Title,
    authors: List[AuthorName],
    year: Option[Year],
    publisher: Option[CollectionEntry.Publisher],
    tags: List[CollectionEntry.Tags],
    identifiers: Map[CollectionEntry.IdentifiersKey, CollectionEntry.IdentifiersValue],
    formats: List[CollectionEntry.Formats]
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object CollectionEntry:
  given Schema[Map[IdentifiersKey, IdentifiersValue]] =
    Schema.schemaForMap[IdentifiersKey, IdentifiersValue](_.value).copy(name = None, isOptional = true)
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Publisher = Publisher.T
  object Publisher extends Newtype[String, Pure]
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]
  type IdentifiersKey = IdentifiersKey.T
  object IdentifiersKey extends Newtype[String, Pure]:
    given JsonKeyCodec[T] = JsonCodec.key[T](_.value, k => Right((IdentifiersKey(_))(k)))
  type IdentifiersValue = IdentifiersValue.T
  object IdentifiersValue extends Newtype[String, Pure]
  type Formats = Formats.T
  object Formats extends Newtype[String, Pure]
