package com.histmersion
package domain.research.workflows

import hearth.kindlings.optics.syntax.*
import com.histmersion.domain.library.Citekey
import com.histmersion.api.research.Coverage

final case class NotUsed(citekey: Citekey, reason: Coverage.Rationale)
