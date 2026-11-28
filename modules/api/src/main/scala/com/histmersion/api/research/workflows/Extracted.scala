package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.research.Coverage
import com.histmersion.util.json.JsonCodec

final case class Extracted(citekey: Citekey, claims: List[SourcedClaim], notUsed: Option[Coverage.Rationale]) derives JsonCodec
