package com.histmersion
package api.automation.planquota

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class RefreshInfo(
    at: RefreshInfo.At,
    mode: RefreshInfo.Mode,
    trigger: RefreshInfo.Trigger,
    ok: RefreshInfo.Ok,
    detail: RefreshInfo.Detail
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object RefreshInfo:
  type LastAttempt = LastAttempt.T
  object LastAttempt extends Newtype[Long, Pure]
  type At = At.T
  object At extends Newtype[String, Pure]
  export RefreshMode.Wire as Mode
  export RefreshTrigger.Wire as Trigger
  enum Ok derives CanEqual:
    case Ok, Failed
  object Ok extends TwoStates[Ok]:
    extension (ok: Ok) def ok: Boolean = ok == Ok
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
