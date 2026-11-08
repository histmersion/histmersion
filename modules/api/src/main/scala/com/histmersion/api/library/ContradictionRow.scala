package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year
import io.github.iltotore.iron.constraint.any.Pure

final case class ContradictionRow(
    citekey: Citekey,
    title: Title,
    year: Option[Year],
    status: Source.Status,
    direction: Option[ContradictionRow.Direction.Wire],
    kind: Option[Edge.Kind],
    otherCitekey: Option[Citekey],
    otherTitle: Option[Title],
    otherYear: Option[Year] = None,
    otherStatus: Option[Source.Status] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object ContradictionRow:
  object Direction:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
