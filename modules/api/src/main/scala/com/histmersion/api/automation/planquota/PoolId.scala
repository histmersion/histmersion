package com.histmersion
package api.automation.planquota

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

enum PoolId(val wire: PoolId.Wire, val title: PoolId.Title) derives CanEqual:
  case ClaudePlan extends PoolId(PoolId.Wire("claude-plan"), PoolId.Title("Claude plan"))
  case ChatgptPlan extends PoolId(PoolId.Wire("chatgpt-plan"), PoolId.Title("ChatGPT plan"))

object PoolId:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[PoolId] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
