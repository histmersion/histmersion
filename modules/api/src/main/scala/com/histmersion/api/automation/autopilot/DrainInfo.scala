package com.histmersion
package api.automation.autopilot

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.json.TwoStates

final case class DrainInfo(
    since: DrainInfo.Since,
    by: DrainInfo.By = DrainInfo.By(""),
    drained: DrainInfo.Drained = DrainInfo.Drained.Running,
    drainedAt: Option[DrainInfo.DrainedAt] = None,
    inFlight: DrainInfo.InFlight = DrainInfo.InFlight(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object DrainInfo:
  type Since = Since.T
  object Since extends Newtype[String, Pure]
  type By = By.T
  object By extends Newtype[String, Pure]
  enum Drained derives CanEqual:
    case Drained, Running
  object Drained extends TwoStates[Drained]:
    extension (drained: Drained) def drained: Boolean = drained == Drained
  type DrainedAt = DrainedAt.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object DrainedAt extends Newtype[String, Pure]
  type InFlight = InFlight.T
  object InFlight extends Newtype[Int, Pure]
