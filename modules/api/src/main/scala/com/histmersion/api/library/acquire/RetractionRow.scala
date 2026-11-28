package com.histmersion
package api.library.acquire

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Doi
import com.histmersion.api.library.RetractionNotice

final case class RetractionRow(
    doi: Doi,
    reason: Option[RetractionNotice.Account],
    date: Option[RetractionRow.Date],
    nature: Option[RetractionRow.Nature]
)

object RetractionRow:
  export RetractionNotice.{Date, Nature}
