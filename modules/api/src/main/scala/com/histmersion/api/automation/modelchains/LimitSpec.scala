package com.histmersion
package api.automation.modelchains

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure

@tolerated(LintRule.G9PrefixRole, "pending G9 (namespaces, not prefixes)")
final case class LimitSpec(amount: LimitSpec.Amount, measure: Measure, window: LimitSpec.Window)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object LimitSpec:
  type Amount = Amount.T
  object Amount extends Newtype[Double, Pure]
  type Window = Window.T
  object Window extends Newtype[String, Pure]
