package com.histmersion
package domain.library.findings

import com.augustnagro.magnum.*
import com.histmersion.domain.library.Confidence

private[library] object FindingColumns:
  /** `findings.confidence`: NULL and an unknown word are both "not stated". There is deliberately no
    * `DbCodec[Confidence]` — a column read as a bare confidence would need a default, and every default claims
    * something.
    */
  given DbCodec[Option[Confidence]] = DbCodec[Option[String]].biMap(
    _.flatMap(s => Confidence.fromString(Confidence.Wire(s.trim))),
    _.map(_.wire.value)
  )
