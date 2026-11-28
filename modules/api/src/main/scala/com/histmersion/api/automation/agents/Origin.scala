package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

enum Origin(val wire: Origin.Wire) derives CanEqual:
  case Person extends Origin(Origin.Wire("person"))
  case Scheduler extends Origin(Origin.Wire("scheduler"))
  case Run extends Origin(Origin.Wire("run"))

  case FromIngest extends Origin(Origin.Wire("ingest"))

  case Manager extends Origin(Origin.Wire("manager"))

  def unattended: Boolean = (this != Person)

object Origin:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Origin] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
