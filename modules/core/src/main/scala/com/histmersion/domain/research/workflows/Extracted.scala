package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library.Citekey
import com.histmersion.api.research.Coverage
import com.histmersion.domain.research.workflows.Claim

final case class Extracted(citekey: Citekey, claims: List[Claim], notUsed: Option[Coverage.Rationale])
