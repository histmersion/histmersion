package com.histmersion.server.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** One canonical skill, `skills/<name>/SKILL.md`: its YAML header as written (kept line by line, so the rendered file
  * stays the author's) and its body.
  */
final case class SkillDef(name: String, header: List[String], body: String):
  def description: String =
    header.find(_.startsWith("description:")).map(_.stripPrefix("description:").trim).getOrElse("")
