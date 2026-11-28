package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema.annotations.description

final case class CheckAnswer(
    @description("The ledger id of your earlier finding (R4, C2, S7…)") id: CheckAnswer.Id,
    @description("true when the current article no longer has the problem") fixed: CheckAnswer.Fixed,
    @description("The current article's words that show it \u2014 the fixed text, or the problem still there \u2014 copied exactly; empty only when the passage was deleted") quote: CheckAnswer.Quote,
    @description("One sentence: what changed, or why it is not fixed (needed when not fixed)") note: CheckAnswer.Note
) derives JsonCodec, KindlingsSchema, Schema

object CheckAnswer:
  export ReviewFinding.Id
  export CheckedFinding.Fixed
  export Quotation.Quote
  export CheckedFinding.Note
