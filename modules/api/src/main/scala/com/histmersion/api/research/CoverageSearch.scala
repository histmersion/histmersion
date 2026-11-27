package com.histmersion
package api.research
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CoverageSearch(query: CoverageSearch.Query, total: CoverageSearch.Total, cited: List[Citekey])
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object CoverageSearch:
  export api.Api.Search.Text as Query
  type Total = Total.T
  object Total extends Newtype[Int, Pure]
