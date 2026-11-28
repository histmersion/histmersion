package com.histmersion
package api.automation.planquota

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.PlanQuota.Rationale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class GuardAnswer(
    allow: GuardAnswer.Allow,
    reason: Option[Rationale] = None,
    resetsAt: Option[GuardAnswer.ResetsAt] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object GuardAnswer:
  enum Allow derives CanEqual:
    case Allow, Deny
  object Allow extends TwoStates[Allow]:
    extension (allow: Allow) def allow: Boolean = allow == Allow
  type ResetsAt = ResetsAt.T
  object ResetsAt extends Newtype[String, Pure]
