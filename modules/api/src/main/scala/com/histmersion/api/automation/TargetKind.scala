package com.histmersion
package api.automation

import io.github.iltotore.iron.constraint.any.Pure
enum TargetKind(val wire: TargetKind.Wire) derives CanEqual:
  case Source extends TargetKind(TargetKind.Wire("source"))
  case Article extends TargetKind(TargetKind.Wire("article"))
  case Goal extends TargetKind(TargetKind.Wire("goal"))
  case Manager extends TargetKind(TargetKind.Wire("manager"))

object TargetKind:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]


  def parse(s: Wire): Option[TargetKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
