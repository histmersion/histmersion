package com.histmersion
package api.research.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.research.Coverage
import com.histmersion.api.research.CritiqueRef
import com.histmersion.util.json.JsonCodec

final case class PairSetAside(id: PairSetAside.Id, pair: CritiqueRef, reason: Coverage.Rationale) derives JsonCodec

object PairSetAside:
  export ReviewFinding.Id
