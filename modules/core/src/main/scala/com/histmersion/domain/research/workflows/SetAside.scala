package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.research.Coverage
import com.histmersion.api.research.CritiqueRef
import com.histmersion.domain.research.workflows.Finding

final case class SetAside(id: SetAside.Id, pair: CritiqueRef, reason: Coverage.Rationale)

object SetAside:
  export Finding.Id
