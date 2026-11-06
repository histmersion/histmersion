package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ContradictionRow(
    citekey: com.histmersion.api.library.Citekey,
    title: com.histmersion.api.library.Title,
    year: Option[com.histmersion.api.catalogue.Year],
    status: com.histmersion.api.library.Source.Status,
    direction: Option[ContradictionRow.Direction.Wire],
    kind: Option[com.histmersion.api.library.Edge.Kind],
    otherCitekey: Option[com.histmersion.api.library.Citekey],
    otherTitle: Option[com.histmersion.api.library.Title],
    otherYear: Option[com.histmersion.api.catalogue.Year] = None,
    otherStatus: Option[com.histmersion.api.library.Source.Status] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ContradictionRow:
  object Direction:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
