package com.histmersion
package domain.automation

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
enum TargetKind(val wire: TargetKind.Wire):
  case Source extends TargetKind(TargetKind.Wire("source"))
  case Article extends TargetKind(TargetKind.Wire("article"))
  case Goal extends TargetKind(TargetKind.Wire("goal"))
  case Manager extends TargetKind(TargetKind.Wire("manager"))

object TargetKind:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[TargetKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
