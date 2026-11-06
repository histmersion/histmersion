package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ResolvedSource(
    citekey: com.histmersion.api.library.Citekey,
    doi: Option[com.histmersion.api.library.Doi],
    authors: List[ResolvedSource.Authors],
    year: Option[com.histmersion.api.catalogue.Year],
    title: ResolvedSource.Title,
    status: com.histmersion.api.library.Source.Status
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def ref: Source.Ref = Source.Ref(com.histmersion.api.library.Citekey(citekey), (doi).map(com.histmersion.api.library.Doi.apply), (authors).map(com.histmersion.api.library.AuthorName.apply), (year).map(com.histmersion.api.catalogue.Year.apply), com.histmersion.api.library.Title((title).value), status)

object ResolvedSource:
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
