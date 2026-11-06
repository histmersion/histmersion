package com.histmersion.api.automation
enum TargetKind(val wire: TargetKind.Wire):
  case Source extends TargetKind(_root_.com.histmersion.api.automation.TargetKind.Wire("source"))
  case Article extends TargetKind(_root_.com.histmersion.api.automation.TargetKind.Wire("article"))
  case Goal extends TargetKind(_root_.com.histmersion.api.automation.TargetKind.Wire("goal"))
  case Manager extends TargetKind(_root_.com.histmersion.api.automation.TargetKind.Wire("manager"))

object TargetKind:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def parse(s: com.histmersion.api.automation.TargetKind.Wire): Option[TargetKind] = values.find(_.wire == _root_.com.histmersion.api.automation.TargetKind.Wire((s).value.trim.toLowerCase))
