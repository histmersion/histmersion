package com.histmersion
package api.library
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

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
  export CalibreGap.Summary
  export CalibreGap.Topics
  export CalibreGap.Provenance
  export CalibreGap.Isbn
  export CalibreGap.StoredOriginal
  type Original = Original.T
  object Original extends Newtype[String, Pure]
  type Counted = Counted.T
  object Counted extends Newtype[Boolean, Pure]
