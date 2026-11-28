package com.histmersion
package api.automation.planquota

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class ModWindow(
    kind: ModWindow.Kind,
    percentUsed: ModWindow.PercentUsed,
    resetsAt: Option[ModWindow.ResetsAt] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ModWindow:
  export WindowStatus.Kind
  export Reading.UsedPct as PercentUsed
  type ResetsAt = ResetsAt.T
  object ResetsAt extends Newtype[String, Pure]
