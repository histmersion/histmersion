package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class PassageResult(
    citekey: com.histmersion.api.library.Citekey,
    query: PassageResult.Query,
    regex: PassageResult.Regex,
    matches: PassageResult.Matches,
    capped: PassageResult.Capped,
    total: PassageResult.Total,
    passages: List[PassageHit],
    bodyChars: PassageResult.BodyChars
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object PassageResult:
  type Query = Query.T
  object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Regex = Regex.T
  object Regex extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Matches = Matches.T
  object Matches extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Capped = Capped.T
  object Capped extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Total = Total.T
  object Total extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyChars = BodyChars.T
  object BodyChars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
