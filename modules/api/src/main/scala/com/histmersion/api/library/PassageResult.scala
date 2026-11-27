package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.Api

@tolerated(LintRule.Prefix, "pending G9 (namespaces, not prefixes)")
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class PassageResult(
    citekey: Citekey,
    query: PassageResult.Query,
    regex: PassageResult.Regex,
    matches: PassageResult.Matches,
    capped: PassageResult.Capped,
    total: PassageResult.Total,
    passages: List[PassageHit],
    bodyChars: PassageResult.BodyChars
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object PassageResult:
  type Query = Query.T
  object Query extends Newtype[String, Pure]
  export Api.PassagesQuery.Regex
  type Matches = Matches.T
  object Matches extends Newtype[Int, Pure]
  type Capped = Capped.T
  object Capped extends Newtype[Boolean, Pure]
  type Total = Total.T
  object Total extends Newtype[Int, Pure]
  type BodyChars = BodyChars.T
  object BodyChars extends Newtype[Int, Pure]
