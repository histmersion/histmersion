package com.histmersion
package api.automation.activity

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

enum Show(val wire: Show.Wire, val label: Show.Label) derives CanEqual:
  case Everything extends Show(Show.Wire("all"), Show.Label("Everything"))
  case Running extends Show(Show.Wire("running"), Show.Label("Running now"))
  case Agents extends Show(Show.Wire("agents"), Show.Label("Agent work"))
  case Jobs extends Show(Show.Wire("jobs"), Show.Label("Background jobs"))
  case Yours extends Show(Show.Wire("yours"), Show.Label("Your actions"))
  case Problems extends Show(Show.Wire("problems"), Show.Label("Problems"))

object Show:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Label = Label.T
  object Label extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Show] =
    val t = (s).value.trim.toLowerCase
    values.find(v => v.wire == Wire(t) || v.toString.toLowerCase == t)
