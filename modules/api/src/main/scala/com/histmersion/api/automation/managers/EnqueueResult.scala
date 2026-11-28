package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class EnqueueResult(
    managerId: ManagerInfo.Id,
    items: List[ManagerItem] = Nil,
    skipped: List[EnqueueResult.Skipped] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object EnqueueResult:
  type Skipped = Skipped.T
  object Skipped extends Newtype[String, Pure]
