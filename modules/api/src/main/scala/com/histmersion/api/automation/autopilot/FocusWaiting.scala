package com.histmersion
package api.automation.autopilot

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.Autopilot.Rationale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class FocusWaiting(
    slug: FocusWaiting.Slug,
    code: FocusWaiting.Code,
    why: Rationale = Rationale(""),
    pinned: FocusWaiting.Pinned = FocusWaiting.Pinned.Unpinned
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object FocusWaiting:
  export api.research.ReviewGoal.Slug
  type Code = Code.T
  object Code extends Newtype[String, Pure]
  enum Pinned derives CanEqual:
    case Pinned, Unpinned
  object Pinned extends TwoStates[Pinned]:
    extension (pinned: Pinned) def pinned: Boolean = pinned == Pinned
