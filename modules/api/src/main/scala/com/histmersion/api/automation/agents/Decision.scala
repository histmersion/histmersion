package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.Newtype

enum Decision(val wire: Decision.Wire) derives CanEqual:
  case Approve extends Decision(Decision.Wire("approve"))
  case Reject extends Decision(Decision.Wire("reject"))

object Decision:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Decision] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
