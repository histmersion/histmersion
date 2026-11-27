package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

final case class CalibreGap(
    citekey: Citekey,
    title: CalibreGap.Title,
    authors: List[CalibreGap.Authors],
    doi: Option[Doi],
    year: Option[Year],
    venue: Option[Source.Venue],
    summary: Option[CalibreGap.Summary],
    topics: List[CalibreGap.Topics],
    provenance: Option[CalibreGap.Provenance],
    calibreId: Option[Source.CalibreId] = None,
    isbn: Option[CalibreGap.Isbn] = None,
    /** None where the server does not say (an older server, the push metadata): the push then tries. */
    storedOriginal: Option[CalibreGap.StoredOriginal] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CalibreGap:
  export com.histmersion.api.library.Title
  type Authors = Authors.T
  object Authors extends Newtype[String, Pure]
  type Summary = Summary.T
  object Summary extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Topics = Topics.T
  object Topics extends Newtype[String, Pure]
  type Provenance = Provenance.T
  object Provenance extends Newtype[String, Pure]
  type Isbn = Isbn.T
  object Isbn extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type StoredOriginal = StoredOriginal.T
  object StoredOriginal extends Newtype[Boolean, Pure]
