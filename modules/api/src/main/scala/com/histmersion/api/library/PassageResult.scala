package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class PassageResult(
    citekey: Citekey,
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
  object Query extends Newtype[String, Pure]
  type Regex = Regex.T
  object Regex extends Newtype[Boolean, Pure]
  type Matches = Matches.T
  object Matches extends Newtype[Int, Pure]
  type Capped = Capped.T
  object Capped extends Newtype[Boolean, Pure]
  type Total = Total.T
  object Total extends Newtype[Int, Pure]
  type BodyChars = BodyChars.T
  object BodyChars extends Newtype[Int, Pure]
