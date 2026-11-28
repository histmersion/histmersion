package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.TwoStates

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
  enum Regex derives CanEqual:
    case Regex, Literal
  object Regex extends TwoStates[Regex]:
    extension (regex: Regex) def regex: Boolean = regex == Regex
  type Matches = Matches.T
  object Matches extends Newtype[Int, Pure]
  enum Capped derives CanEqual:
    case Capped, Complete
  object Capped extends TwoStates[Capped]:
    extension (capped: Capped) def capped: Boolean = capped == Capped
  type Total = Total.T
  object Total extends Newtype[Int, Pure]
  type BodyChars = BodyChars.T
  object BodyChars extends Newtype[Int, Pure]
