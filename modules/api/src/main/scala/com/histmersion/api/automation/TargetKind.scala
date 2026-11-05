package com.histmersion.api.automation
enum TargetKind(val wire: String):
  case Source extends TargetKind("source")
  case Article extends TargetKind("article")
  case Goal extends TargetKind("goal")
  case Manager extends TargetKind("manager")

object TargetKind:
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.automation.TargetKind.ParseS): Option[TargetKind] = values.find(_.wire == s.trim.toLowerCase)
