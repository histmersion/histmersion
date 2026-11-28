package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class ItemPage(
    items: List[ManagerItem],
    total: ItemPage.Total,
    offset: ItemPage.Offset,
    limit: ItemPage.Limit
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ItemPage:
  type Total = Total.T
  object Total extends Newtype[Int, Pure]
  type Offset = Offset.T
  object Offset extends Newtype[Int, Pure]
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
