package com.histmersion.values

/** What a pipeline works on: a source (by citekey), an article (by slug), a goal (by slug) or a process manager (by
  * id).
  */
enum TargetKind(val wire: String):
  case Source extends TargetKind("source")
  case Article extends TargetKind("article")
  case Goal extends TargetKind("goal")
  case Manager extends TargetKind("manager")

object TargetKind:
  def parse(s: String): Option[TargetKind] = values.find(_.wire == s.trim.toLowerCase)
