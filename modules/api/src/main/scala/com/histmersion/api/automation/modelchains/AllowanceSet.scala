package com.histmersion
package api.automation.modelchains

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class AllowanceSet(
    target: AllowanceSet.Target,
    limits: List[LimitSpec] = Nil,
    reservePct: AllowanceSet.ReservePct = Allowance.ReservePct(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object AllowanceSet:
  type Target = Target.T
  object Target extends Newtype[String, Pure]
  export Allowance.ReservePct
