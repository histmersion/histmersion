package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.JsonCodec
import io.github.iltotore.iron.constraint.any.Pure

final case class ParkedPicks(waiting: ParkedPicks.Waiting, known: ParkedPicks.Known) derives JsonCodec

object ParkedPicks:
  type Waiting = Waiting.T
  object Waiting extends Newtype[Int, Pure]
  type Known = Known.T
  object Known extends Newtype[Int, Pure]
