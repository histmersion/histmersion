package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.automation.Run
import com.histmersion.domain.ReviewArticle
import com.histmersion.domain.automation
import com.histmersion.domain.research.workflows.Reviewer
import com.histmersion.domain.research.workflows.Merged
import com.histmersion.domain.research.workflows.Check
import com.histmersion.domain.research.workflows.Quotation

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
  export automation.Run.Id as Run
  export Check.Note
  export Quotation.Quote
