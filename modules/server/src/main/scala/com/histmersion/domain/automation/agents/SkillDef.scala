package com.histmersion.domain.automation.agents

import scala.annotation.tailrec

import scala.compiletime.asMatchable
import scala.jdk.CollectionConverters.*

/** One canonical skill, `skills/<name>/SKILL.md`: its YAML header as written (kept line by line, so the rendered file
  * stays the author's) and its body.
  */
final case class SkillDef(name: SkillDef.Name, header: List[SkillDef.Header], body: SkillDef.Body):
  def description: String =
    header.find(_.value.startsWith("description:")).map(_.value.stripPrefix("description:").trim).getOrElse("")

object SkillDef:

  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Header = Header.T
  object Header extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Body = Body.T
  object Body extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
