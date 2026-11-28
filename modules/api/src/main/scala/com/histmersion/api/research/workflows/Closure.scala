package com.histmersion
package api.research.workflows

import com.histmersion.api.automation.agents.RunInfo
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.util.json.JsonCodec

final case class Closure(
    by: Closure.By,
    round: Closure.Round,
    run: Closure.Run,
    note: Closure.Note = Closure.Note(""),
    quote: Closure.Quote = Closure.Quote(""),
    citekey: Option[Citekey] = None
) derives JsonCodec

object Closure:
  export Reviewer.Wire as By
  export Merged.Round
  export CheckedFinding.Note
  export Quotation.Quote
  export RunInfo.Id as Run
