package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** One source the library lists as not in a collection: its push metadata, where its original is (`local`, `stored`,
  * `none`, `unknown`) and whether it counts as a gap (a record with no fetched file never does).
  */
@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class CollectionGapRow(
    citekey: Citekey,
    title: Title,
    authors: List[AuthorName],
    doi: Option[Doi],
    year: Option[Year],
    venue: Option[Source.Venue],
    summary: Option[CollectionGapRow.Summary],
    topics: List[CollectionGapRow.Topics],
    provenance: Option[CollectionGapRow.Provenance],
    calibreId: Option[Source.CalibreId],
    isbn: Option[CollectionGapRow.Isbn],
    storedOriginal: Option[CollectionGapRow.StoredOriginal],
    original: CollectionGapRow.Original,
    counted: CollectionGapRow.Counted
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CollectionGapRow:
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]
  type Topics = Topics.T
  object Topics extends Newtype[String, Pure]
  type Provenance = Provenance.T
  object Provenance extends Newtype[String, Pure]
  type Isbn = Isbn.T
  object Isbn extends Newtype[String, Pure]
  type StoredOriginal = StoredOriginal.T
  object StoredOriginal extends Newtype[Boolean, Pure]
  type Original = Original.T
  object Original extends Newtype[String, Pure]
  type Counted = Counted.T
  object Counted extends Newtype[Boolean, Pure]
