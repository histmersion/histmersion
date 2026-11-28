package com.histmersion
package api.checks.lint

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.checks.Lint.all
import com.histmersion.api.checks.Lint.ItemLimit

final case class Rule(name: Rule.Name, description: Rule.Description)

object Rule:
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Description = Description.T
  object Description extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rule(name: Rule.Name): Option[Rule] = all.find(_.name == Rule.Name((name).value.trim.toLowerCase))

  extension (r: Rule)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def report(items: List[LintItem], maxItems: ItemLimit): LintRuleReport =
      LintRuleReport(r.name, r.description, LintRuleReport.Count(items.size), items.take(math.max(0, (maxItems).value)))
