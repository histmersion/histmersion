package com.histmersion
package api.checks.lint

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.checks.Lint

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class LintRuleReport(
    rule: LintRuleReport.Rule,
    description: LintRuleReport.Description,
    count: LintRuleReport.Count,
    items: List[LintItem]
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object LintRuleReport:
  export Rule.{Description, Name as Rule}
  type Count = Count.T
  object Count extends Newtype[Int, Pure]
