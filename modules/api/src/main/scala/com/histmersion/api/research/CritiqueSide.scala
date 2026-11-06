package com.histmersion.api.research
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CritiqueSide(
    citekey: com.histmersion.api.library.Citekey,
    title: Option[com.histmersion.api.library.Title] = None,
    year: Option[com.histmersion.api.catalogue.Year] = None,
    cited: CritiqueSide.Cited = _root_.com.histmersion.api.research.CritiqueSide.Cited(false),
    mentions: CritiqueSide.Mentions = _root_.com.histmersion.api.research.CritiqueSide.Mentions(0)
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CritiqueSide:
  type Cited = Cited.T
  object Cited extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mentions = Mentions.T
  object Mentions extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
