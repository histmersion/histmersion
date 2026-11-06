package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CalibreGap(
    citekey: com.histmersion.api.library.Citekey,
    title: CalibreGap.Title,
    authors: List[CalibreGap.Authors],
    doi: Option[com.histmersion.api.library.Doi],
    year: Option[com.histmersion.api.catalogue.Year],
    venue: Option[com.histmersion.api.library.Source.Venue],
    summary: Option[CalibreGap.Summary],
    topics: List[CalibreGap.Topics],
    provenance: Option[CalibreGap.Provenance],
    calibreId: Option[com.histmersion.api.library.Source.CalibreId] = None,
    isbn: Option[CalibreGap.Isbn] = None,
    /** None where the server does not say (an older server, the push metadata): the push then tries. */
    storedOriginal: Option[CalibreGap.StoredOriginal] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CalibreGap:
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
