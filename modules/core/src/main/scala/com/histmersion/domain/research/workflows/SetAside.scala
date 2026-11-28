package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.research.Coverage
import com.histmersion.api.research.CritiqueRef
import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.domain.research.workflows.Finding

final case class SetAside(id: SetAside.Id, pair: CritiqueRef, reason: Coverage.Rationale) derives JsonCodec

object SetAside:
  export Finding.Id
