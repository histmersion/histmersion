package com.histmersion.domain.automation
enum TargetKind(val wire: String):
  case Source extends TargetKind("source")
  case Article extends TargetKind("article")
  case Goal extends TargetKind("goal")
  case Manager extends TargetKind("manager")

object TargetKind:
  def parse(s: String): Option[TargetKind] = values.find(_.wire == s.trim.toLowerCase)
