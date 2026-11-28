package com.histmersion
package domain.reviewarticle

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.domain.reviewarticle.Finding
import com.histmersion.domain.reviewarticle.Check
import com.histmersion.domain.reviewarticle.Evidence

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class CheckAnswer(
    id: CheckAnswer.Id,
    fixed: CheckAnswer.Fixed,
    quote: CheckAnswer.Quote,
    note: CheckAnswer.Note
) derives JsonCodec

object CheckAnswer:
  export Finding.Id
  export Check.Fixed
  export Evidence.Quote
  export Check.Note

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  @tolerated(LintRule.G48, "pending G48 (schemas from Kindlings annotations)")
  given Schema[CheckAnswer] = KindlingsSchema
    .derived[CheckAnswer]
    .schema
    .modify(_.id)(_.description("The ledger id of your earlier finding (R4, C2, S7…)"))
    .modify(_.fixed)(_.description("true when the current article no longer has the problem"))
    .modify(_.quote)(
      _.description(
        "The current article's words that show it — the fixed text, or the problem still there — copied exactly; " +
          "empty only when the passage was deleted"
      )
    )
    .modify(_.note)(_.description("One sentence: what changed, or why it is not fixed (needed when not fixed)"))
