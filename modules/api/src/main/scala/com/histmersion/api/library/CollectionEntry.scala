package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.Schema
import com.histmersion.lint.{tolerated, LintRule}

/** One item of a collection as a listing shows it (a Calibre book): its id, title, authors, year, publisher, tags,
  * identifiers by scheme and its files.
  */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
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
      Schema

object CollectionEntry:
  type Id = Id.T
  object Id extends Newtype[String, Pure]
  type Publisher = Publisher.T
  object Publisher extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
    extension (publisher: Publisher) def nonEmpty: Boolean = publisher.value.nonEmpty
  type Tags = Tags.T
  object Tags extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type IdentifiersKey = IdentifiersKey.T
  object IdentifiersKey extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type IdentifiersValue = IdentifiersValue.T
  object IdentifiersValue extends Newtype[String, Pure]
  type Formats = Formats.T
  object Formats extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
