package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One source the library lists as not in a collection: its push metadata, where its original is (`local`, `stored`,
  * `none`, `unknown`) and whether it counts as a gap (a record with no fetched file never does).
  */
final case class CollectionGapRow(
    citekey: com.histmersion.api.library.Citekey,
    title: com.histmersion.api.library.Title,
    authors: List[com.histmersion.api.library.AuthorName],
    doi: Option[com.histmersion.api.library.Doi],
    year: Option[com.histmersion.api.catalogue.Year],
    venue: Option[com.histmersion.api.library.Venue],
    summary: Option[CollectionGapRow.Summary],
    topics: List[CollectionGapRow.Topics],
    provenance: Option[CollectionGapRow.Provenance],
    calibreId: Option[com.histmersion.api.library.Source.CalibreId],
    isbn: Option[CollectionGapRow.Isbn],
    storedOriginal: Option[CollectionGapRow.StoredOriginal],
    original: CollectionGapRow.Original,
    counted: CollectionGapRow.Counted
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionGapRow:
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Topics = Topics.T
  object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provenance = Provenance.T
  object Provenance extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Isbn = Isbn.T
  object Isbn extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoredOriginal = StoredOriginal.T
  object StoredOriginal extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Original = Original.T
  object Original extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Counted = Counted.T
  object Counted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
