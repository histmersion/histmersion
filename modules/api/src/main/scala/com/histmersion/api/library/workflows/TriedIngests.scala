package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec

final case class TriedIngests(ingested: List[IngestedWork] = Nil, failed: List[FailedIngest] = Nil) derives JsonCodec
