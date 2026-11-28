package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class PickedWork(candidate: RankableWork, why: PickedWork.Rationale) derives JsonCodec

object PickedWork:
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]
