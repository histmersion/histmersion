package com.histmersion
package api.automation

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
enum TargetKind(val wire: TargetKind.Wire) derives CanEqual:
  case Source extends TargetKind(TargetKind.Wire("source"))
  case Article extends TargetKind(TargetKind.Wire("article"))
  case Goal extends TargetKind(TargetKind.Wire("goal"))
  case Manager extends TargetKind(TargetKind.Wire("manager"))

object TargetKind:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[TargetKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
