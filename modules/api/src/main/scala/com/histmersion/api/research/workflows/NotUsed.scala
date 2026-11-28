package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.api.research.Coverage
import com.histmersion.util.json.JsonCodec

final case class NotUsed(citekey: Citekey, reason: Coverage.Rationale) derives JsonCodec
