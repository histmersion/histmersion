package com.histmersion
package api.personal

import com.histmersion.api.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class UnreadPage(
    rows: List[UnreadRow],
    total: UnreadPage.Total,
    offset: UnreadPage.Offset = UnreadPage.Offset(0)
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object UnreadPage:
  type Total = Total.T
  object Total extends Newtype[Int, Pure]
  type Offset = Offset.T
  object Offset extends Newtype[Int, Pure]
