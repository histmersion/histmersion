package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import io.github.iltotore.iron.constraint.any.Pure

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
enum StepKind(val wire: StepKind.Wire):
  case Code extends StepKind(StepKind.Wire("code"))
  case Llm extends StepKind(StepKind.Wire("llm"))

object StepKind:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def parse(s: Wire): Option[StepKind] = values.find(_.wire == s)
