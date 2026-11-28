package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.agents.Usage
import com.histmersion.api.automation.agents.Price
import com.histmersion.api.automation.PlanQuota
import com.histmersion.api.automation.planquota.PoolId

enum Billing:
  case Plan(pool: PoolId)
  case Metered(price: Price)

  def cost(u: Usage): BigDecimal = this match
    case Plan(_)    => BigDecimal(0)
    case Metered(p) => p.cost(u)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def describe: String = this match
    case Plan(p)    => (s"plan pool: ${p.wire}")
    case Metered(p) => (s"price: ${p.text} USD per million tokens")
